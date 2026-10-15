package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.security.config.JwtProperties;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.security.online.OnlineSessionRules;
import com.acme.scaffold.security.online.RestrictionRules;
import com.acme.scaffold.security.token.RefreshTokenService;
import com.acme.scaffold.system.dto.LoginRestrictionView;
import com.acme.scaffold.system.dto.OnlineUserQuery;
import com.acme.scaffold.system.dto.OnlineUserView;
import com.acme.scaffold.system.dto.RestrictLoginRequest;
import com.acme.scaffold.system.entity.SysLoginRestrictionDO;
import com.acme.scaffold.system.entity.SysOnlineSessionDO;
import com.acme.scaffold.system.entity.SysUserDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在线用户管理服务（V27）：在线会话跟踪、强制下线、临时登录限制。
 *
 * <p><b>数据模型</b>：一行 {@code sys_online_session} = 一个在线设备会话，
 * 业务键 {@code (tenant_id, user_id, device_id)}（device_id 即前端 X-Device-Id 头）。
 * 登录登记 → 请求心跳保活 → 登出/强制下线/限制连带下线置 {@code OFFLINE}。
 * 行不物理删除，历史行作为「最近下线记录」留存。
 *
 * <p><b>与刷新令牌的关系</b>：强制下线同时吊销该设备的刷新令牌并置会话离线；
 * 已签发 access token 由 {@code OnlineSessionFilter} 在下一个请求时校验拦截（401），
 * 无需等待其自然过期，实现「操作后实时生效」。
 *
 * <p><b>限制登录语义</b>：写入 {@code sys_login_restriction}（限时+原因），
 * 登录链路 {@code AuthApplicationService} 强制校验；限制生效时同步强制下线其全部在线会话，
 * 避免「禁止新登录但在线会话继续操作」的漏洞。到期由 {@code LoginRestrictionJob} 自动解除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserService {

    /** 下线原因（sys_online_session.offline_reason）。 */
    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_FORCE_LOGOUT = "FORCE_LOGOUT";
    public static final String REASON_RESTRICTED = "RESTRICTED";
    public static final String REASON_INACTIVITY = "INACTIVITY";
    public static final String REASON_PASSWORD_CHANGED = "PASSWORD_CHANGED";

    private final DSLContext dsl;
    private final RefreshTokenService refreshTokenService;
    private final SecurityContextFacade securityContextFacade;
    private final JwtProperties jwtProperties;

    // ------------------------------------------------------------------
    // 会话登记与心跳（登录链路 / OnlineSessionFilter 调用）
    // ------------------------------------------------------------------

    /**
     * 登录成功后登记在线会话（upsert）：同设备重复登录重置登录时间与会话状态，
     * 而非插入多行——「一次登录一个设备一行」保证列表与强制下线的精确性。
     */
    @Transactional
    public void registerSession(Long userId, String username, String displayName, String deviceId,
                                String clientId, String ip, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        SysOnlineSessionDO exist = JooqWriters.fetchOne(dsl, JooqTables.SYS_ONLINE_SESSION,
                SysOnlineSessionDO.class,
                DSL.and(JooqTables.SYS_ONLINE_SESSION.field("user_id", Long.class).eq(userId),
                        deviceEq(deviceId)));
        if (exist == null) {
            SysOnlineSessionDO row = new SysOnlineSessionDO();
            row.setUserId(userId);
            row.setDeviceId(deviceId);
            row.setClientId(StringUtils.hasText(clientId) ? clientId : "web");
            row.setUsername(username);
            row.setDisplayName(displayName);
            row.setLoginIp(ip);
            row.setUserAgent(truncate(userAgent, 500));
            row.setLoginAt(now);
            row.setLastActiveAt(now);
            row.setStatus(SysOnlineSessionDO.STATUS_ONLINE);
            JooqWriters.insert(dsl, JooqTables.SYS_ONLINE_SESSION, row);
        } else {
            // 重新登录：重置登录/活跃时间并恢复在线态；offline_reason 需显式置空（updateById 跳 null）
            SysOnlineSessionDO patch = new SysOnlineSessionDO();
            patch.setLoginIp(ip);
            patch.setUserAgent(truncate(userAgent, 500));
            patch.setClientId(StringUtils.hasText(clientId) ? clientId : "web");
            patch.setLoginAt(now);
            patch.setLastActiveAt(now);
            patch.setStatus(SysOnlineSessionDO.STATUS_ONLINE);
            patch.setDisplayName(displayName);
            JooqWriters.updateByIdWithNulls(dsl, JooqTables.SYS_ONLINE_SESSION, exist.getId(), patch,
                    Set.of("offline_reason"));
        }
    }

    /** 心跳写库：仅更新在线行的最近活跃时间（OFFLINE 行不复活，防止被踢会话靠心跳回归）。 */
    public void touch(Long userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return;
        }
        dsl.update(JooqTables.SYS_ONLINE_SESSION.table())
                .set(JooqTables.SYS_ONLINE_SESSION.field("last_active_at", java.sql.Timestamp.class),
                        java.sql.Timestamp.valueOf(LocalDateTime.now()))
                .where(JooqTables.SYS_ONLINE_SESSION.field("user_id", Long.class).eq(userId)
                        .and(JooqTables.SYS_ONLINE_SESSION.field("device_id", String.class).eq(deviceId))
                        .and(JooqTables.SYS_ONLINE_SESSION.field("status", String.class)
                                .eq(SysOnlineSessionDO.STATUS_ONLINE)))
                .execute();
    }

    /**
     * 会话是否仍然在线（过滤器校验用）。
     *
     * @return {@code true}=在线放行；{@code false}=行存在但已下线（应 401）；
     *         {@code null}=无会话行（改造前签发的历史令牌等），调用方按 fail-open 放行
     */
    public Boolean sessionState(Long userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return null;
        }
        SysOnlineSessionDO row = JooqWriters.fetchOne(dsl, JooqTables.SYS_ONLINE_SESSION,
                SysOnlineSessionDO.class,
                DSL.and(JooqTables.SYS_ONLINE_SESSION.field("user_id", Long.class).eq(userId),
                        JooqTables.SYS_ONLINE_SESSION.field("device_id", String.class).eq(deviceId)));
        if (row == null) {
            return null;
        }
        return SysOnlineSessionDO.STATUS_ONLINE.equals(row.getStatus());
    }

    /** 登出 / 改密：把该用户全部在线会话置离线。 */
    @Transactional
    public int markOfflineByUser(Long userId, String reason) {
        if (userId == null) {
            return 0;
        }
        return dsl.update(JooqTables.SYS_ONLINE_SESSION.table())
                .set(JooqTables.SYS_ONLINE_SESSION.field("status", String.class), SysOnlineSessionDO.STATUS_OFFLINE)
                .set(JooqTables.SYS_ONLINE_SESSION.field("offline_reason", String.class), reason)
                .where(JooqTables.SYS_ONLINE_SESSION.field("user_id", Long.class).eq(userId)
                        .and(JooqTables.SYS_ONLINE_SESSION.field("status", String.class)
                                .eq(SysOnlineSessionDO.STATUS_ONLINE)))
                .execute();
    }

    /**
     * 会话空闲回收联动（由 {@code SessionCleanupService} 周期调用）：
     * 最近活跃早于阈值的在线会话置离线——其刷新令牌已在同一轮回收中被吊销，
     * 若不同步置离线，列表将长期残留「空闲」幽灵会话。
     *
     * @param cutoff 空闲截止时间（now - session-max-inactive）
     * @return 置离线的行数
     */
    @Transactional
    public int releaseInactiveSessions(LocalDateTime cutoff) {
        return dsl.update(JooqTables.SYS_ONLINE_SESSION.table())
                .set(JooqTables.SYS_ONLINE_SESSION.field("status", String.class), SysOnlineSessionDO.STATUS_OFFLINE)
                .set(JooqTables.SYS_ONLINE_SESSION.field("offline_reason", String.class), REASON_INACTIVITY)
                .where(JooqTables.SYS_ONLINE_SESSION.field("status", String.class)
                        .eq(SysOnlineSessionDO.STATUS_ONLINE)
                        .and(JooqTables.SYS_ONLINE_SESSION.field("last_active_at", java.sql.Timestamp.class)
                                .lessThan(java.sql.Timestamp.valueOf(cutoff))))
                .execute();
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * 分页查询在线会话。状态筛选口径与 {@link OnlineSessionRules#displayStatus} 一致：
     * RESTRICTED 优先于 IDLE/ONLINE（用户存在生效限制即视为已限制）。
     */
    public PageResult<OnlineUserView> page(OnlineUserQuery query) {
        LocalDateTime now = LocalDateTime.now();
        String username = OnlineUserQuery.normalize(query.username());
        String ip = OnlineUserQuery.normalize(query.ip());
        String status = OnlineUserQuery.normalize(query.status());

        Set<Long> restrictedUserIds = activeRestrictedUserIds(now);

        List<Condition> conds = new ArrayList<>();
        conds.add(JooqTables.SYS_ONLINE_SESSION.field("status", String.class)
                .eq(SysOnlineSessionDO.STATUS_ONLINE));
        if (username != null) {
            conds.add(JooqTables.SYS_ONLINE_SESSION.field("username", String.class)
                    .like("%" + username + "%"));
        }
        if (ip != null) {
            conds.add(JooqTables.SYS_ONLINE_SESSION.field("login_ip", String.class).like("%" + ip + "%"));
        }
        if ("RESTRICTED".equalsIgnoreCase(status)) {
            if (restrictedUserIds.isEmpty()) {
                return PageResult.empty(query.page(), query.size());
            }
            conds.add(JooqTables.SYS_ONLINE_SESSION.field("user_id", Long.class).in(restrictedUserIds));
        } else if ("ONLINE".equalsIgnoreCase(status) || "IDLE".equalsIgnoreCase(status)) {
            LocalDateTime cutoff = now.minus(jwtProperties.getOnlineSessionIdleThreshold());
            boolean idle = "IDLE".equalsIgnoreCase(status);
            conds.add(idle
                    ? JooqTables.SYS_ONLINE_SESSION.field("last_active_at", java.sql.Timestamp.class)
                    .lessThan(java.sql.Timestamp.valueOf(cutoff))
                    : JooqTables.SYS_ONLINE_SESSION.field("last_active_at", java.sql.Timestamp.class)
                    .greaterOrEqual(java.sql.Timestamp.valueOf(cutoff)));
            if (!restrictedUserIds.isEmpty()) {
                conds.add(JooqTables.SYS_ONLINE_SESSION.field("user_id", Long.class)
                        .notIn(restrictedUserIds));
            }
        }

        var pageQuery = query.toPageQuery();
        PageResult<SysOnlineSessionDO> page = JooqWriters.page(dsl, JooqTables.SYS_ONLINE_SESSION,
                SysOnlineSessionDO.class, DSL.and(conds), pageQuery,
                // 默认：最近活跃倒序（最活跃在前），id 兜底保证翻页稳定
                JooqTables.SYS_ONLINE_SESSION.field("last_active_at", java.sql.Timestamp.class).desc(),
                JooqTables.SYS_ONLINE_SESSION.field("id", Long.class).desc());

        Map<Long, String> freshNames = freshDisplayNames(page.records());
        List<OnlineUserView> views = page.records().stream()
                .map(row -> new OnlineUserView(
                        row.getId(),
                        row.getUserId(),
                        row.getUsername(),
                        freshNames.getOrDefault(row.getUserId(), row.getDisplayName()),
                        row.getLoginIp(),
                        row.getLoginAt(),
                        row.getLastActiveAt(),
                        OnlineSessionRules.displayStatus(row.getLastActiveAt(),
                                restrictedUserIds.contains(row.getUserId()),
                                jwtProperties.getOnlineSessionIdleThreshold(), now),
                        row.getDeviceId(),
                        row.getUserAgent()))
                .toList();
        return new PageResult<>(page.page(), page.size(), page.total(), views);
    }

    /** 分页查询登录限制记录（结构化操作日志）。 */
    public PageResult<LoginRestrictionView> restrictionPage(int page, int size, String username,
                                                            String status) {
        List<Condition> conds = new ArrayList<>();
        String kw = username == null ? null : username.trim();
        if (StringUtils.hasText(kw)) {
            conds.add(JooqTables.SYS_LOGIN_RESTRICTION.field("username", String.class)
                    .like("%" + kw + "%"));
        }
        if (StringUtils.hasText(status)) {
            conds.add(JooqTables.SYS_LOGIN_RESTRICTION.field("status", String.class)
                    .eq(status.trim().toUpperCase()));
        }
        var pageQuery = com.acme.scaffold.common.api.PageQuery.of(page, size);
        PageResult<SysLoginRestrictionDO> result = JooqWriters.page(dsl, JooqTables.SYS_LOGIN_RESTRICTION,
                SysLoginRestrictionDO.class, DSL.and(conds), pageQuery,
                // 默认：限制时间倒序（最新限制在前）
                JooqTables.SYS_LOGIN_RESTRICTION.field("restricted_at", java.sql.Timestamp.class).desc(),
                JooqTables.SYS_LOGIN_RESTRICTION.field("id", Long.class).desc());
        List<LoginRestrictionView> views = result.records().stream()
                .map(r -> new LoginRestrictionView(
                        r.getId(), r.getUserId(), r.getUsername(), r.getDisplayName(), r.getReason(),
                        r.getDurationMinutes(), r.getRestrictedByName(), r.getRestrictedAt(),
                        r.getExpireAt(), r.getReleasedAt(), r.getReleaseType(), r.getStatus()))
                .toList();
        return new PageResult<>(result.page(), result.size(), result.total(), views);
    }

    /** 查询用户当前生效的限制（登录链路拦截用）；无则返回 null。 */
    public SysLoginRestrictionDO findActiveRestriction(Long userId) {
        if (userId == null) {
            return null;
        }
        List<SysLoginRestrictionDO> actives = JooqWriters.fetchList(dsl, JooqTables.SYS_LOGIN_RESTRICTION,
                SysLoginRestrictionDO.class,
                DSL.and(JooqTables.SYS_LOGIN_RESTRICTION.field("user_id", Long.class).eq(userId),
                        JooqTables.SYS_LOGIN_RESTRICTION.field("status", String.class)
                                .eq(SysLoginRestrictionDO.STATUS_ACTIVE)));
        LocalDateTime now = LocalDateTime.now();
        return actives.stream()
                .filter(r -> RestrictionRules.isActive(r, now))
                // 长期限制（expireAt 为空）优先返回，其次到期最晚的
                .max((a, b) -> {
                    if (a.getExpireAt() == null) return b.getExpireAt() == null ? 0 : 1;
                    if (b.getExpireAt() == null) return -1;
                    return a.getExpireAt().compareTo(b.getExpireAt());
                })
                .orElse(null);
    }

    // ------------------------------------------------------------------
    // 写操作
    // ------------------------------------------------------------------

    /**
     * 批量强制下线：吊销目标会话对应设备的刷新令牌并置会话离线。
     * 已不在线的会话行直接跳过（幂等），全部跳过时抛 NOT_FOUND 由前端提示。
     */
    @Transactional
    public int forceLogout(List<Long> sessionIds) {
        List<SysOnlineSessionDO> rows = JooqWriters.fetchList(dsl, JooqTables.SYS_ONLINE_SESSION,
                SysOnlineSessionDO.class,
                DSL.and(JooqTables.SYS_ONLINE_SESSION.field("id", Long.class).in(sessionIds),
                        JooqTables.SYS_ONLINE_SESSION.field("status", String.class)
                                .eq(SysOnlineSessionDO.STATUS_ONLINE)));
        if (rows.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "目标会话不存在或已下线");
        }
        int affected = 0;
        for (SysOnlineSessionDO row : rows) {
            // 先吊销刷新令牌（设备侧失效），再置会话离线（列表/过滤器可见）
            refreshTokenService.revokeByUserAndDevice(row.getUserId(), row.getDeviceId());
            dsl.update(JooqTables.SYS_ONLINE_SESSION.table())
                    .set(JooqTables.SYS_ONLINE_SESSION.field("status", String.class),
                            SysOnlineSessionDO.STATUS_OFFLINE)
                    .set(JooqTables.SYS_ONLINE_SESSION.field("offline_reason", String.class),
                            REASON_FORCE_LOGOUT)
                    .where(JooqTables.SYS_ONLINE_SESSION.field("id", Long.class).eq(row.getId()))
                    .execute();
            affected++;
            log.info("强制下线会话 userId={} deviceId={} ip={}", row.getUserId(), row.getDeviceId(),
                    row.getLoginIp());
        }
        return affected;
    }

    /**
     * 设置临时限制登录：写入限制记录并同步强制下线该用户全部在线会话。
     *
     * <p>连带下线是安全必需项：若仅禁止新登录而保留在线会话，被限制用户可继续操作
     * 至刷新令牌自然过期（最长 30 天），限制形同虚设。
     */
    @Transactional
    public Long restrict(Long userId, RestrictLoginRequest request) {
        SysUserDO user = JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class, userId);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        if (!RestrictionRules.isAllowedDuration(request.durationMinutes())) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "限制时长不合法");
        }
        if (findActiveRestriction(userId) != null) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该用户已被限制登录，请先解除");
        }

        LocalDateTime now = LocalDateTime.now();
        SysLoginRestrictionDO restriction = new SysLoginRestrictionDO();
        restriction.setUserId(userId);
        restriction.setUsername(user.getUsername());
        restriction.setDisplayName(user.getDisplayName());
        restriction.setReason(request.reason().trim());
        restriction.setDurationMinutes(request.durationMinutes());
        restriction.setRestrictedAt(now);
        restriction.setExpireAt(RestrictionRules.computeExpireAt(now, request.durationMinutes()));
        CurrentOperator operator = currentOperator();
        restriction.setRestrictedBy(operator.id());
        restriction.setRestrictedByName(operator.name());
        JooqWriters.insert(dsl, JooqTables.SYS_LOGIN_RESTRICTION, restriction);

        // 连带强制下线：按用户全量吊销刷新令牌 + 全部在线会话置离线
        refreshTokenService.revokeByUser(userId);
        int offline = markOfflineByUser(userId, REASON_RESTRICTED);
        log.info("设置限制登录 userId={} durationMinutes={} 连带下线会话数={}",
                userId, request.durationMinutes(), offline);
        return restriction.getId();
    }

    /** 手动解除登录限制（幂等）：解除该用户全部生效中的限制记录。 */
    @Transactional
    public int release(Long userId) {
        if (userId == null) {
            return 0;
        }
        CurrentOperator operator = currentOperator();
        return dsl.update(JooqTables.SYS_LOGIN_RESTRICTION.table())
                .set(JooqTables.SYS_LOGIN_RESTRICTION.field("status", String.class),
                        SysLoginRestrictionDO.STATUS_RELEASED)
                .set(JooqTables.SYS_LOGIN_RESTRICTION.field("released_at", java.sql.Timestamp.class),
                        java.sql.Timestamp.valueOf(LocalDateTime.now()))
                .set(JooqTables.SYS_LOGIN_RESTRICTION.field("released_by", Long.class), operator.id())
                .set(JooqTables.SYS_LOGIN_RESTRICTION.field("release_type", String.class),
                        SysLoginRestrictionDO.RELEASE_MANUAL)
                .where(JooqTables.SYS_LOGIN_RESTRICTION.field("user_id", Long.class).eq(userId)
                        .and(JooqTables.SYS_LOGIN_RESTRICTION.field("status", String.class)
                                .eq(SysLoginRestrictionDO.STATUS_ACTIVE)))
                .execute();
    }

    /**
     * 到期自动解除（定时任务）：ACTIVE 且已过到期时间的限制记录置为 RELEASED。
     * 与登录链路的 {@link RestrictionRules#isActive} 口径互为兜底：
     * 任务未执行的窗口期内，过期记录在登录侧同样判为不生效。
     *
     * @return 本次自动解除的条数
     */
    @Transactional
    public int releaseExpired() {
        int updated = dsl.update(JooqTables.SYS_LOGIN_RESTRICTION.table())
                .set(JooqTables.SYS_LOGIN_RESTRICTION.field("status", String.class),
                        SysLoginRestrictionDO.STATUS_RELEASED)
                .set(JooqTables.SYS_LOGIN_RESTRICTION.field("released_at", java.sql.Timestamp.class),
                        java.sql.Timestamp.valueOf(LocalDateTime.now()))
                .set(JooqTables.SYS_LOGIN_RESTRICTION.field("release_type", String.class),
                        SysLoginRestrictionDO.RELEASE_AUTO_EXPIRED)
                .where(JooqTables.SYS_LOGIN_RESTRICTION.field("status", String.class)
                        .eq(SysLoginRestrictionDO.STATUS_ACTIVE)
                        .and(JooqTables.SYS_LOGIN_RESTRICTION.field("expire_at", java.sql.Timestamp.class)
                                .isNotNull())
                        .and(JooqTables.SYS_LOGIN_RESTRICTION.field("expire_at", java.sql.Timestamp.class)
                                .lessOrEqual(java.sql.Timestamp.valueOf(LocalDateTime.now()))))
                .execute();
        if (updated > 0) {
            log.info("登录限制到期自动解除 {} 条", updated);
        }
        return updated;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /** 生效限制的用户 ID 集合（RESTRICTED 判定与筛选的唯一数据源）。 */
    private Set<Long> activeRestrictedUserIds(LocalDateTime now) {
        List<SysLoginRestrictionDO> actives = JooqWriters.fetchList(dsl, JooqTables.SYS_LOGIN_RESTRICTION,
                SysLoginRestrictionDO.class,
                JooqTables.SYS_LOGIN_RESTRICTION.field("status", String.class)
                        .eq(SysLoginRestrictionDO.STATUS_ACTIVE));
        return actives.stream()
                .filter(r -> RestrictionRules.isActive(r, now))
                .map(SysLoginRestrictionDO::getUserId)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 批量获取最新姓名（display_name 是登录快照，用户改名后列表应展示最新值）。
     * 空集合不发查询；用户已删时保留快照值。
     */
    private Map<Long, String> freshDisplayNames(List<SysOnlineSessionDO> rows) {
        Set<Long> userIds = rows.stream().map(SysOnlineSessionDO::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserDO> users = JooqWriters.fetchList(dsl, JooqTables.SYS_USER, SysUserDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_USER),
                        JooqTables.SYS_USER.field("id", Long.class).in(userIds)));
        Map<Long, String> names = new LinkedHashMap<>();
        for (SysUserDO u : users) {
            names.put(u.getId(), u.getDisplayName());
        }
        return names;
    }

    /** device_id 可空等值条件：null 匹配 IS NULL，与唯一键 (user_id, device_id) 语义一致。 */
    private Condition deviceEq(String deviceId) {
        Field<String> f = JooqTables.SYS_ONLINE_SESSION.field("device_id", String.class);
        return deviceId == null ? f.isNull() : f.eq(deviceId);
    }

    private CurrentOperator currentOperator() {
        return securityContextFacade.getCurrentPrincipal()
                .map(p -> new CurrentOperator(p.userId(), p.displayName()))
                .orElse(new CurrentOperator(null, null));
    }

    private record CurrentOperator(Long id, String name) {
    }

    private static String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
