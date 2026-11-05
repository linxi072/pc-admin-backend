package com.acme.scaffold.security.permission;

/**
 * 数据权限默认兜底策略（P0-1，fail-closed 改造）。
 *
 * <p>当无法从角色/规则推导出明确数据范围时（用户无角色、无规则、机构类规则解析不出机构、
 * 或规则 scope_type 非法/为空），不再统一「不限制」（fail-open，可见全量数据），
 * 而是回落到本策略产出最小可见结果。</p>
 *
 * <ul>
 *   <li>{@link #ALL}：兜底不限制（fail-open，兼容旧语义，仅应显式配置）；</li>
 *   <li>{@link #SELF}：兜底仅本人（fail-closed，最小可见，默认）。</li>
 * </ul>
 *
 * <p>默认 {@code SELF}：配置缺失即最小暴露面，契合「最小权限」基线；确需「无规则即全量」
 * 的场景（如超管兜底）应显式将 {@code app.datascope.default-policy} 设为 {@code ALL}。</p>
 */
public enum DataScopeDefaultPolicy {

    /** 兜底不限制（fail-open，可见全部数据）。 */
    ALL,

    /** 兜底仅本人数据（fail-closed，最小可见）。 */
    SELF
}
