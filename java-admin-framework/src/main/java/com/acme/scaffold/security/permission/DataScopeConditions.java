package com.acme.scaffold.security.permission;

import com.acme.scaffold.jooq.JooqTables;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.impl.DSL;

/**
 * 把 {@link DataScopeContext} 里的范围结果翻译成 jOOQ 查询条件，供 Service 直接拼进 WHERE。
 *
 * <p>规则（与 {@link DefaultDataScopeProvider} 的解析语义一一对应）：
 * <ul>
 *   <li>无上下文 / {@code ALL} → 不加条件；</li>
 *   <li>{@code SELF} → 有「本人」列时按 {@code userColumn = 当前用户}；没有时退化为
 *       「本人所属部门」子查询（如部门树），用户未挂部门则查不到任何行；</li>
 *   <li>{@code DEPT / DEPT_AND_CHILD / CUSTOM} → {@code orgColumn IN (机构集合)}；</li>
 *   <li>机构集合为空 → 不加条件（与 Provider 的「配了空集合降级为不限」保持一致）。</li>
 * </ul>
 *
 * <p>表上不存在目标列时记 WARN 并不加条件：配置错误应当可观测，但不应让列表接口 500。
 */
@Slf4j
public final class DataScopeConditions {

    private DataScopeConditions() {
    }

    /** 机构维度过滤；该资源没有「本人」语义（如部门树）时用此重载。 */
    public static Condition of(JooqTables.TableRef table, String orgColumn) {
        return of(table, orgColumn, null);
    }

    /**
     * 机构 + 本人维度过滤。
     *
     * @param orgColumn  机构列名，如 sys_user.org_id
     * @param userColumn 「本人」列名，如 sys_user.id；该资源没有本人语义时传 null
     */
    public static Condition of(JooqTables.TableRef table, String orgColumn, String userColumn) {
        DataScopeContext.Scope scope = DataScopeContext.current();
        if (scope == null || scope.result() == null) {
            return DSL.trueCondition();
        }
        DataScopeResult result = scope.result();
        if (result.unrestricted()) {
            return DSL.trueCondition();
        }
        if (result.selfOnly()) {
            if (scope.userId() == null) {
                return DSL.trueCondition();
            }
            if (userColumn != null && table.has(userColumn)) {
                return table.field(userColumn, Long.class).eq(scope.userId());
            }
            if (orgColumn != null && table.has(orgColumn)) {
                // 没有「本人」列的资源（如部门树）：退化为「本人所属部门」
                return table.field(orgColumn, Long.class).in(orgOfUser(scope.userId()));
            }
            return DSL.trueCondition();
        }
        if (orgColumn == null || !table.has(orgColumn)) {
            log.warn("数据权限列缺失，本次查询不加过滤: table={}, orgColumn={}", table.name(), orgColumn);
            return DSL.trueCondition();
        }
        Set<Long> orgIds = result.orgIds();
        if (orgIds == null || orgIds.isEmpty()) {
            return DSL.trueCondition();
        }
        return table.field(orgColumn, Long.class).in(orgIds);
    }

    /** 取本人所属部门的子查询；用户未挂部门时结果为空，对应「查不到任何行」。 */
    private static Select<Record1<Long>> orgOfUser(Long userId) {
        return DSL.select(JooqTables.SYS_USER.field("org_id", Long.class))
                .from(JooqTables.SYS_USER.table())
                .where(JooqTables.SYS_USER.field("id", Long.class).eq(userId));
    }
}
