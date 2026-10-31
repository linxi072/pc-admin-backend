package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooqgen.Tables;
import com.acme.scaffold.security.jwt.JwtProperties;
import com.acme.scaffold.security.token.port.OnlineSessionReleasePort;
import com.acme.scaffold.security.token.service.RefreshTokenService;
import com.acme.scaffold.system.dto.LoginRestrictionView;
import com.acme.scaffold.system.dto.OnlineUserQuery;
import com.acme.scaffold.system.dto.OnlineUserView;
import com.acme.scaffold.system.dto.RestrictLoginRequest;
import com.acme.scaffold.system.entity.SysLoginRestrictionDO;
import com.acme.scaffold.system.entity.SysOnlineSessionDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.online.rules.OnlineSessionRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在线用户管理服务（V27，C2 重构：登录限制子域已抽离至 {@link LoginRestrictionService}）。
 *
 * <p><b>数据模型</b>：一行 {@code sys_online_session} = 一个在线设备会话，
 * 业务键 {@code (tenant_id, user_id, device_id)}（device_id 即前端 X-Device-Id 头）。
 * 登录登记 → 请求心跳保活 → 登出/强制下线/限制连带下线置 {@code OFFLINE}。</p>
 *
 * <p><b>限制登录语义</b>：写入 {@code sys_login_restriction}（限时+原因），登录链路
 * {@code AuthService} 强制校验；限制生效时同步强制下线其全部在线会话。该逻辑现由
 * {@link LoginRestrictionService} 承载，本类仅做委托，公开方法签名不变。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUserService implements OnlineSessionReleasePort {
    private static final JooqTables.TableRef USER = JooqTables.toTableRef(Tables.SYS_USER);

    /** 下线原因（sys_online_session.offline_reason）。 */
    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_FORCE_LOGOUT = "FORCE_LOGOUT";
    public static final String REASON_RESTRICTED = "RESTRICTED";
    public static final String REASON_INACTIVITY = "INACTIVITY";
    public static final String REASON_PASSWORD_CHANGED = "PASSWORD_CHANGED";

    private static final JooqTables.TableRef OS = JooqTables.toTableRef(Tables.SYS_ONLINE_SESSION);

    private final DSLContext dsl;
    private final RefreshTokenService refreshTokenService;
    private final SecurityContextFacade securityContextFacade;
    private final JwtProperties jwtProperties;
    private final LoginRestrictionService loginRestrictionService;

    // ------------------------------------------------------------------
    // 会话登记与心跳（登录链路 / OnlineSessionFilter 调用）
    // ------------------------------------------------------------------

    /**
     * 登录成功后登记在线会话（upsert）：同设备重复登录重置登录时间与会话状态。
     */
    @Transactional
    public void registerSession(Long userId, String username, String displayName, String deviceId,
                                String clientId, String ip, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        SysOnlineSessionDO exist = JooqWriters.fetchOne(dsl, OS,
                SysOnlineSessionDO.class,
                DSL.and(OS.field("user_id", Long.class).eq(userId),
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
            JooqWriters.insert(dsl, OS, row);
        } else {
            SysOnlineSessionDO patch = new SysOnlineSessionDO();
            patch.setLoginIp(ip);
            patch.setUserAgent(truncate(userAgent, 500));
            patch.setClientId(StringUtils.hasText(clientId) ? clientId : "web");
            patch.setLoginAt(now);
            patch.setLastActiveAt(now);
            patch.setStatus(SysOnlineSessionDO.STATUS_ONLINE);
            patch.setDisplayName(displayName);
            JooqWriters.updateByIdWithNulls(dsl, OS, exist.getId(), patch,
                    Set.of("offline_reason"));
        }
    }

    /** 心跳写库：仅更新在线行的最近活跃时间（OFFLINE 行不复活）。 */
    public void touch(Long userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return;
        }
        dsl.update(OS.table())
                .set(OS.field("last_active_at", Timestamp.class),
                        Timestamp.valueOf(LocalDateTime.now()))
                .where(OS.field("user_id", Long.class).eq(userId)
                        .and(OS.field("device_id", String.class).eq(deviceId))
                        .and(OS.field("status", String.class)
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
        SysOnlineSessionDO row = JooqWriters.fetchOne(dsl, OS,
                SysOnlineSessionDO.class,
                DSL.and(OS.field("user_id", Long.class).eq(userId),
                        OS.field("device_id", String.class).eq(deviceId)));
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
        return dsl.update(OS.table())
                .set(OS.field("status", String.class), SysOnlineSessionDO.STATUS_OFFLINE)
                .set(OS.field("offline_reason", String.class), reason)
                .where(OS.field("user_id", Long.class).eq(userId)
                        .and(OS.field("status", String.class)
                                .eq(SysOnlineSessionDO.STATUS_ONLINE)))
                .execute();
    }

    /**
     * 会话空闲回收联动（由 {@code SessionCleanupService} 周期调用）。
     *
     * @param cutoff 空闲截止时间（now - session-max-inactive）
     * @return 置离线的行数
     */
    @Transactional
    @Override
    public int releaseInactiveSessions(LocalDateTime cutoff) {
        return dsl.update(OS.table())
                .set(OS.field("status", String.class), SysOnlineSessionDO.STATUS_OFFLINE)
                .set(OS.field("offline_reason", String.class), REASON_INACTIVITY)
                .where(OS.field("status", String.class)
                        .eq(SysOnlineSessionDO.STATUS_ONLINE)
                        .and(OS.field("last_active_at", Timestamp.class)
                                .lessThan(Timestamp.valueOf(cutoff))))
                .execute();
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * 分页查询在线会话。状态筛选口径与 {@link OnlineSessionRules#displayStatus} 一致：
     * RESTRICTED 优先于 IDLE/ONLINE。
     */
    public PageResult<OnlineUserView> page(OnlineUserQuery query) {
        LocalDateTime now = LocalDateTime.now();
        String username = OnlineUserQuery.normalize(query.username());
        String ip = OnlineUserQuery.normalize(query.ip());
        String status = OnlineUserQuery.normalize(query.status());

        Set<Long> restrictedUserIds = loginRestrictionService.activeRestrictedUserIds(now);

        List<Condition> conds = new ArrayList<>();
        conds.add(OS.field("status", String.class)
                .eq(SysOnlineSessionDO.STATUS_ONLINE));
        if (username != null) {
            conds.add(OS.field("username", String.class)
                    .like("%" + username + "%"));
        }
        if (ip != null) {
            conds.add(OS.field("login_ip", String.class).like("%" + ip + "%"));
        }
        if ("RESTRICTED".equalsIgnoreCase(status)) {
            if (restrictedUserIds.isEmpty()) {
                return PageResult.empty(query.page(), query.size());
            }
            conds.add(OS.field("user_id", Long.class).in(restrictedUserIds));
        } else if ("ONLINE".equalsIgnoreCase(status) || "IDLE".equalsIgnoreCase(status)) {
            LocalDateTime cutoff = now.minus(jwtProperties.getOnlineSessionIdleThreshold());
            boolean idle = "IDLE".equalsIgnoreCase(status);
            conds.add(idle
                    ? OS.field("last_active_at", Timestamp.class)
                    .lessThan(Timestamp.valueOf(cutoff))
                    : OS.field("last_active_at", Timestamp.class)
                    .greaterOrEqual(Timestamp.valueOf(cutoff)));
            if (!restrictedUserIds.isEmpty()) {
                conds.add(OS.field("user_id", Long.class)
                        .notIn(restrictedUserIds));
            }
        }

        var pageQuery = query.toPageQuery();
        PageResult<SysOnlineSessionDO> page = JooqWriters.page(dsl, OS,
                SysOnlineSessionDO.class, DSL.and(conds), pageQuery,
                OS.field("last_active_at", Timestamp.class).desc(),
                OS.field("id", Long.class).desc());

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

    // ------------------------------------------------------------------
    // 写操作（登录限制子域委托 LoginRestrictionService）
    // ------------------------------------------------------------------

    /** 分页查询登录限制记录。 */
    public PageResult<LoginRestrictionView> restrictionPage(int page, int size, String username, String status) {
        return loginRestrictionService.restrictionPage(page, size, username, status);
    }

    /** 查询用户当前生效的限制（登录链路拦截用）；无则返回 null。 */
    public SysLoginRestrictionDO findActiveRestriction(Long userId) {
        return loginRestrictionService.findActiveRestriction(userId);
    }

    /** 设置临时限制登录：写入限制记录并同步强制下线该用户全部在线会话。 */
    @Transactional
    public Long restrict(Long userId, RestrictLoginRequest request) {
        return loginRestrictionService.restrict(userId, request);
    }

    /** 手动解除登录限制（幂等）。 */
    @Transactional
    public int release(Long userId) {
        return loginRestrictionService.release(userId);
    }

    /** 到期自动解除（定时任务）。 */
    @Transactional
    public int releaseExpired() {
        return loginRestrictionService.releaseExpired();
    }

    // ------------------------------------------------------------------
    // 强制下线（本服务内聚）
    // ------------------------------------------------------------------

    /**
     * 批量强制下线：吊销目标会话对应设备的刷新令牌并置会话离线。
     */
    @Transactional
    public int forceLogout(List<Long> sessionIds) {
        List<SysOnlineSessionDO> rows = JooqWriters.fetchList(dsl, OS,
                SysOnlineSessionDO.class,
                DSL.and(OS.field("id", Long.class).in(sessionIds),
                        OS.field("status", String.class)
                                .eq(SysOnlineSessionDO.STATUS_ONLINE)));
        if (rows.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "目标会话不存在或已下线");
        }
        int affected = 0;
        for (SysOnlineSessionDO row : rows) {
            refreshTokenService.revokeByUserAndDevice(row.getUserId(), row.getDeviceId());
            dsl.update(OS.table())
                    .set(OS.field("status", String.class),
                            SysOnlineSessionDO.STATUS_OFFLINE)
                    .set(OS.field("offline_reason", String.class),
                            REASON_FORCE_LOGOUT)
                    .where(OS.field("id", Long.class).eq(row.getId()))
                    .execute();
            affected++;
            log.info("强制下线会话 userId={} deviceId={} ip={}", row.getUserId(), row.getDeviceId(),
                    row.getLoginIp());
        }
        return affected;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /**
     * 批量获取最新姓名（display_name 是登录快照，用户改名后列表应展示最新值）。
     */
    private Map<Long, String> freshDisplayNames(List<SysOnlineSessionDO> rows) {
        Set<Long> userIds = rows.stream().map(SysOnlineSessionDO::getUserId).collect(java.util.stream.Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserDO> users = JooqWriters.fetchList(dsl, USER, SysUserDO.class,
                DSL.and(JooqWriters.notDeleted(USER),
                        USER.field("id", Long.class).in(userIds)));
        Map<Long, String> names = new LinkedHashMap<>();
        for (SysUserDO u : users) {
            names.put(u.getId(), u.getDisplayName());
        }
        return names;
    }

    /** device_id 可空等值条件：null 匹配 IS NULL，与唯一键 (user_id, device_id) 语义一致。 */
    private Condition deviceEq(String deviceId) {
        Field<String> f = OS.field("device_id", String.class);
        return deviceId == null ? f.isNull() : f.eq(deviceId);
    }

    private static String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
