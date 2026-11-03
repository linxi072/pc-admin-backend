package com.acme.scaffold.common.event.domain;

import com.acme.scaffold.common.event.DomainEvent;

import java.util.List;

/**
 * 用户被禁用事件（ROADMAP R2 · 跨域副作用示范）。
 *
 * <p>由 {@code system} 模块在「用户状态由非 DISABLED 转为 DISABLED」时发布。
 * 订阅方（如 {@code security} 踢下线 / {@code notification} 发通知）各自响应，
 * {@code system} 不再直连这些下游，实现模块解耦。</p>
 *
 * <p>携带禁用前后上下文，便于订阅方决策（例如仅对「曾经可用」的账号执行踢下线）。</p>
 */
public class UserDisabledEvent extends DomainEvent {

    /** 被禁用用户主键。 */
    private final Long userId;

    /** 用户名（审计 / 通知展示用）。 */
    private final String username;

    /** 禁用前的状态（如 ACTIVE / LOCKED），用于区分「首次禁用」与「状态回切」。 */
    private final String previousStatus;

    /** 触发禁用的操作人（审计轨迹，可为 null 表示系统行为）。 */
    private final String operator;

    /** 该用户被解除绑定的角色编码（订阅方可按需处理）。 */
    private final List<String> revokedRoleCodes;

    public UserDisabledEvent(Long tenantId, String source, Long userId, String username,
                             String previousStatus, String operator, List<String> revokedRoleCodes) {
        super(tenantId, source);
        this.userId = userId;
        this.username = username;
        this.previousStatus = previousStatus;
        this.operator = operator;
        this.revokedRoleCodes = revokedRoleCodes;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public String getOperator() {
        return operator;
    }

    public List<String> getRevokedRoleCodes() {
        return revokedRoleCodes;
    }
}
