package com.acme.scaffold.security.user;

/**
 * 账号数据访问端口（防腐层）。
 *
 * <p>由 {@code security.user} 定义、仅返回 security 自有 {@link UserAccount} 快照，
 * system 侧提供适配器实现，承载对 {@code sys_user} 表的 jOOQ 读取，
 * 消除 security 包对 {@code system.entity} 的直连（B1/B5 同款解耦约束）。</p>
 */
public interface UserAccountDataPort {

    /** 按用户名加载账号；不存在返回 {@code null}。 */
    UserAccount loadByUsername(String username);

    /** 按主键加载账号；不存在返回 {@code null}。 */
    UserAccount loadById(Long id);

    /** 按主键部分更新账号；仅 {@link UserAccountPatch} 中的非空字段参与写入。 */
    void updateById(Long id, UserAccountPatch patch);
}
