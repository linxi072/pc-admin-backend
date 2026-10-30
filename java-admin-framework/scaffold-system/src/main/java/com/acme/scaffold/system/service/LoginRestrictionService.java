package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooqgen.Tables;
import com.acme.scaffold.security.token.service.RefreshTokenService;
import com.acme.scaffold.system.dto.LoginRestrictionView;
import com.acme.scaffold.system.dto.RestrictLoginRequest;
import com.acme.scaffold.system.entity.SysLoginRestrictionDO;
import com.acme.scaffold.system.entity.SysOnlineSessionDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.online.rules.RestrictionRules;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录限制子域（从 {@link OnlineUserService} 抽离，C2 重构）。
 *
 * <p>职责：临时限制登录记录的写入 / 分页查询 / 主动解除 / 到期自动解除，
 * 以及「用户当前生效限制」查询（登录链路拦截用）。连带强制下线逻辑经由注入的
 * {@link OnlineUserService} 反向调用（通过接口约定 {@link #markOfflineByUser} 等价能力），
 * 但为避免循环依赖，吊销刷新令牌与置离线直接复用 {@link RefreshTokenService} 与 dsl，
 * 与抽离前行为一致。{@link OnlineUserService} 仅做委托，公开方法签名不变。
 */
@Service
@RequiredArgsConstructor
public class LoginRestrictionService {

    private static final JooqTables.TableRef LR = JooqTables.toTableRef(Tables.SYS_LOGIN_RESTRICTION);
    private static final JooqTables.TableRef OS = JooqTables.toTableRef(Tables.SYS_ONLINE_SESSION);

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;
    private final RefreshTokenService refreshTokenService;

    /** 分页查询登录限制记录（结构化操作日志）。 */
    public PageResult<LoginRestrictionView> restrictionPage(int page, int size, String username,
                                                            String status) {
        List<Condition> conds = new ArrayList<>();
        String kw = username == null ? null : username.trim();
        if (StringUtils.hasText(kw)) {
            conds.add(Tables.SYS_LOGIN_RESTRICTION.USERNAME.like("%" + kw + "%"));
        }
        if (StringUtils.hasText(status)) {
            conds.add(Tables.SYS_LOGIN_RESTRICTION.STATUS.eq(status.trim().toUpperCase()));
        }
        var pageQuery = PageQuery.of(page, size);
        PageResult<SysLoginRestrictionDO> result = JooqWriters.page(dsl, LR,
                SysLoginRestrictionDO.class, DSL.and(conds), pageQuery,
                Tables.SYS_LOGIN_RESTRICTION.RESTRICTED_AT.desc(),
                Tables.SYS_LOGIN_RESTRICTION.ID.desc());
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
        List<SysLoginRestrictionDO> actives = JooqWriters.fetchList(dsl, LR,
                SysLoginRestrictionDO.class,
                DSL.and(Tables.SYS_LOGIN_RESTRICTION.USER_ID.eq(userId),
                        Tables.SYS_LOGIN_RESTRICTION.STATUS
                                .eq(SysLoginRestrictionDO.STATUS_ACTIVE)));
        LocalDateTime now = LocalDateTime.now();
        return actives.stream()
                .filter(r -> RestrictionRules.isActive(r, now))
                .max((a, b) -> {
                    if (a.getExpireAt() == null) return b.getExpireAt() == null ? 0 : 1;
                    if (b.getExpireAt() == null) return -1;
                    return a.getExpireAt().compareTo(b.getExpireAt());
                })
                .orElse(null);
    }

    /**
     * 设置临时限制登录：写入限制记录并同步强制下线该用户全部在线会话。
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
        JooqWriters.insert(dsl, LR, restriction);

        // 连带强制下线：按用户全量吊销刷新令牌 + 全部在线会话置离线
        refreshTokenService.revokeByUser(userId);
        int offline = dsl.update(OS.table())
                .set(OS.field("status", String.class),
                        SysOnlineSessionDO.STATUS_OFFLINE)
                .set(OS.field("offline_reason", String.class),
                        OnlineUserService.REASON_RESTRICTED)
                .where(OS.field("user_id", Long.class).eq(userId)
                        .and(OS.field("status", String.class)
                                .eq(SysOnlineSessionDO.STATUS_ONLINE)))
                .execute();
        return restriction.getId();
    }

    /** 手动解除登录限制（幂等）：解除该用户全部生效中的限制记录。 */
    @Transactional
    public int release(Long userId) {
        if (userId == null) {
            return 0;
        }
        CurrentOperator operator = currentOperator();
        return dsl.update(LR.table())
                .set(Tables.SYS_LOGIN_RESTRICTION.STATUS, SysLoginRestrictionDO.STATUS_RELEASED)
                .set(Tables.SYS_LOGIN_RESTRICTION.RELEASED_AT, LocalDateTime.now())
                .set(Tables.SYS_LOGIN_RESTRICTION.RELEASED_BY, operator.id())
                .set(Tables.SYS_LOGIN_RESTRICTION.RELEASE_TYPE, SysLoginRestrictionDO.RELEASE_MANUAL)
                .where(Tables.SYS_LOGIN_RESTRICTION.USER_ID.eq(userId)
                        .and(Tables.SYS_LOGIN_RESTRICTION.STATUS
                                .eq(SysLoginRestrictionDO.STATUS_ACTIVE)))
                .execute();
    }

    /**
     * 到期自动解除（定时任务）：ACTIVE 且已过到期时间的限制记录置为 RELEASED。
     * 与登录链路的 {@link RestrictionRules#isActive} 口径互为兜底。
     *
     * @return 本次自动解除的条数
     */
    @Transactional
    public int releaseExpired() {
        int updated = dsl.update(LR.table())
                .set(Tables.SYS_LOGIN_RESTRICTION.STATUS, SysLoginRestrictionDO.STATUS_RELEASED)
                .set(Tables.SYS_LOGIN_RESTRICTION.RELEASED_AT, LocalDateTime.now())
                .set(Tables.SYS_LOGIN_RESTRICTION.RELEASE_TYPE, SysLoginRestrictionDO.RELEASE_AUTO_EXPIRED)
                .where(Tables.SYS_LOGIN_RESTRICTION.STATUS
                        .eq(SysLoginRestrictionDO.STATUS_ACTIVE)
                        .and(Tables.SYS_LOGIN_RESTRICTION.EXPIRE_AT.isNotNull())
                        .and(Tables.SYS_LOGIN_RESTRICTION.EXPIRE_AT
                                .lessOrEqual(LocalDateTime.now())))
                .execute();
        return updated;
    }

    /** 生效限制的用户 ID 集合（RESTRICTED 判定与筛选的唯一数据源）。 */
    public Set<Long> activeRestrictedUserIds(LocalDateTime now) {
        List<SysLoginRestrictionDO> actives = JooqWriters.fetchList(dsl, LR,
                SysLoginRestrictionDO.class,
                Tables.SYS_LOGIN_RESTRICTION.STATUS
                        .eq(SysLoginRestrictionDO.STATUS_ACTIVE));
        return actives.stream()
                .filter(r -> RestrictionRules.isActive(r, now))
                .map(SysLoginRestrictionDO::getUserId)
                .collect(Collectors.toSet());
    }

    private CurrentOperator currentOperator() {
        return securityContextFacade.getCurrentPrincipal()
                .map(p -> new CurrentOperator(p.userId(), p.displayName()))
                .orElse(new CurrentOperator(null, null));
    }

    private record CurrentOperator(Long id, String name) {
    }
}
