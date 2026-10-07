package com.acme.scaffold.security.permission;

/**
 * 数据权限解析扩展点。
 *
 * <p><b>「空集合 = 不限制」的旧约定已废弃</b>：它让「查无规则」与「命中 ALL 规则」
 * 返回同一个结果，调用方无法区分；一旦解析过程出错就会静默退化成全量可见（越权）。
 * 现改为返回显式的范围类型，把「不限」与「无数据」区分开。
 *
 * <p>实现方须保证：
 * <ul>
 *   <li>用户无角色或查无规则 => {@link DataScopeType#ALL}（保持历史行为：未配置即不限制）；</li>
 *   <li>命中 ALL 规则 => {@link DataScopeType#ALL}；</li>
 *   <li>SELF 优先于机构类规则，命中即短路（更严格的语义优先）。</li>
 * </ul>
 */
public interface DataScopeProvider {

    /**
     * 计算用户在指定资源上的数据权限范围。
     *
     * @param userId       当前用户 ID
     * @param resourceCode 资源编码，例如 {@code system:user}
     * @return 显式的范围结果，不允许返回 null
     */
    DataScopeResult resolve(Long userId, String resourceCode);
}
