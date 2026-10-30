package com.acme.scaffold.security.online;

/**
 * 登录限制数据访问端口（防腐层）。
 *
 * <p>由 {@code security.online} 定义、返回 security 自有 {@link LoginRestriction} 快照，
 * system 侧提供适配器实现，消除 security 对 {@code system.entity} 的直连
 * （B1/B5 同款解耦约束）。</p>
 */
public interface LoginRestrictionDataPort {

    /** 查询用户当前生效的限制；无则返回 {@code null}。 */
    LoginRestriction findActive(Long userId);
}
