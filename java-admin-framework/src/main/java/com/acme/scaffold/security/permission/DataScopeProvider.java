package com.acme.scaffold.security.permission;

import java.util.Set;

/**
 * 数据权限解析扩展点。
 * 返回允许访问的机构 ID 集合；返回空集合表示“不限制”（即 ALL）。
 */
public interface DataScopeProvider {

    Set<Long> resolveOrgIds(Long userId, String resourceCode);
}
