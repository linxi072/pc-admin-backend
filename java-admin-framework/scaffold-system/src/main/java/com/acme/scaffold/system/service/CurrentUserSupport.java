package com.acme.scaffold.system.service;
import com.acme.scaffold.jooq.dsl.FetchSupport;

import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.entity.SysUserDO;
import org.jooq.DSLContext;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 当前登录用户相关共享静态支撑方法。
 *
 * <p>{@code AnnouncementReadService} / {@code MessageReadService} / {@code MessageService} 的
 * {@code requireCurrentUserId}、{@code ProfileService} / {@code ProfilePasswordService} 的 {@code loadCurrentUser}、
 * 以及 {@code AnnouncementReadService} / {@code MessageReadService} 的 {@code loadDisplayNames}
 * 原本各自内联了完全相同（或仅差别于资源表引用）的实现；此处收敛为单一事实来源，
 * 各服务保留同名私有方法作为委托壳（调用方零改动）。所有方法均针对 {@code sys_user} 表。</p>
 */
final class CurrentUserSupport {

    private CurrentUserSupport() {
    }

    /** 当前登录用户 id（未认证返回 {@code null}）。 */
    static Long currentUserId(SecurityContextFacade facade) {
        return facade.getCurrentPrincipal().map(p -> p.userId()).orElse(null);
    }

    /** 当前登录用户 id（未认证抛 {@link CommonErrorCode#UNAUTHORIZED}）。 */
    static Long requireCurrentUserId(SecurityContextFacade facade) {
        Long userId = currentUserId(facade);
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "未获取到当前登录用户");
        }
        return userId;
    }

    /** 当前登录用户实体（不存在抛 {@link CommonErrorCode#NOT_FOUND}）。 */
    static SysUserDO loadCurrentUser(DSLContext dsl, SecurityContextFacade facade) {
        Long userId = facade.requireCurrentPrincipal().userId();
        SysUserDO user = FetchSupport.requirePresent(JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class, userId), "用户不存在");
        return user;
    }

    /** 批量装载用户显示名：userId → displayName（用户被删时缺项，视图降级展示 userId）。 */
    static Map<Long, String> loadDisplayNames(DSLContext dsl, Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return JooqWriters.fetchList(dsl, JooqTables.SYS_USER, SysUserDO.class,
                        JooqTables.SYS_USER.field("id", Long.class).in(userIds)).stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(SysUserDO::getId, SysUserDO::getDisplayName, (a, b) -> a));
    }
}
