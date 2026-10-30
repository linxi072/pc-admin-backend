package com.acme.scaffold.security.token.service;

import java.util.List;
import java.util.Set;

/**
 * Token 版本数据访问端口（防腐层）。
 *
 * <p>由 {@code security.token.service} 定义、仅返回抽象类型（用户主键），system 侧提供适配器实现，
 * 承载对 {@code sys_user_role} 关联表的查询，消除 security 包对 {@code system.entity} 的直连
 * （B1/B5 同款解耦约束）。</p>
 */
public interface TokenVersionDataPort {

    /** 按角色 id 集合反查持有这些角色的全部用户 id（去重）。 */
    List<Long> userIdsByRoleIds(Set<Long> roleIds);
}
