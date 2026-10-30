package com.acme.scaffold.security.permission;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 数据权限解析结果。
 *
 * <p>把三件此前混在一起的事显式分开：
 * <ul>
 *   <li>{@link #type()} —— 范围类型（不限 / 仅本人 / 机构 / 机构及下级 / 自定义）；</li>
 *   <li>{@link #orgIds()} —— 机构集合，{@link DataScopeType#SELF} 时为空（ SELF 按本人过滤，不按机构）；</li>
 *   <li>{@link #unrestricted()} —— 是否不限制，避免调用方再写一次「空集合即不限」的隐式约定。</li>
 * </ul>
 *
 * @param type   范围类型
 * @param orgIds 允许访问的机构 ID 集合；SELF 时为空集
 */
public record DataScopeResult(DataScopeType type, Set<Long> orgIds) {

    /** 不限制（全部数据）。 */
    public static DataScopeResult all() {
        return new DataScopeResult(DataScopeType.ALL, Set.of());
    }

    /** 仅本人数据。 */
    public static DataScopeResult self() {
        return new DataScopeResult(DataScopeType.SELF, Set.of());
    }

    /**
     * 限定在给定机构集合内。
     *
     * @param orgIds 机构集合；为空时降级为 {@link #all()}，避免「配置了机构规则但集合为空」导致查不到任何数据
     */
    public static DataScopeResult ofOrgs(Set<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return all();
        }
        return new DataScopeResult(DataScopeType.CUSTOM, Collections.unmodifiableSet(new LinkedHashSet<>(orgIds)));
    }

    /** 是否不限制。 */
    public boolean unrestricted() {
        return type == DataScopeType.ALL;
    }

    /** 是否仅本人（SELF 不按机构过滤，机构集合无意义）。 */
    public boolean selfOnly() {
        return type == DataScopeType.SELF;
    }
}
