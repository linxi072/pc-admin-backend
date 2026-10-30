package com.acme.scaffold.jooq.dsl;

import com.acme.scaffold.jooq.dsl.JooqTables.TableRef;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 租户相关表的分类工具（M19 正式立项 / SaaS 生产启用）。
 *
 * <p>与 {@link JooqTables} 同包（两者强耦合：本类通过反射收集其中的 {@link TableRef}），
 * 避免 {@code common.tenant} 与 {@code jooq.dsl} 形成包级循环依赖。
 *
 * <p>区分「平台共享维表（豁免隔离，tenant_id 恒为 0）」与「业务数据表（按租户隔离）」：
 * <ul>
 *   <li>{@link #PLATFORM_TABLES}：与 {@code TenantProperties.platformTables} 默认列表一致，
 *       由平台统一维护（租户注册、菜单、字典、配置、API 资源）；</li>
 *   <li>{@link #businessTableRefs()}：通过反射收集 {@link JooqTables} 中除平台维表外的全部
 *       {@code TableRef}，新增业务表自动纳入，避免手写清单漂移。</li>
 * </ul>
 *
 * <p>用于「存量数据归位（backfill）」「跨租户数据迁移（reassign）」「跨租户数据总览」等
 * 平台治理动作，统一以业务表集合为操作面，绝不会误改平台维表。</p>
 */
public final class TenantTables {

    /** 平台共享维表（豁免隔离）。与 TenantProperties.platformTables 默认列表严格一致。 */
    public static final Set<String> PLATFORM_TABLES = Set.of(
            "sys_tenant", "sys_menu", "sys_dict_type", "sys_dict_data", "sys_config", "sys_api_resource");

    private TenantTables() {
    }

    /** 给定表名是否为平台共享维表。 */
    public static boolean isPlatformTable(String tableName) {
        return tableName != null && PLATFORM_TABLES.contains(tableName);
    }

    /** 全部业务表（JooqTables 中除平台维表外的所有 TableRef）。新增表自动纳入。 */
    public static List<TableRef> businessTableRefs() {
        List<TableRef> refs = new ArrayList<>();
        for (Field f : JooqTables.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == TableRef.class) {
                try {
                    TableRef ref = (TableRef) f.get(null);
                    if (ref != null && !PLATFORM_TABLES.contains(ref.name())) {
                        refs.add(ref);
                    }
                } catch (IllegalAccessException ignored) {
                    // 本类可见的静态字段，正常情况下不会抛
                }
            }
        }
        return Collections.unmodifiableList(refs);
    }

    /** 全部业务表名（有序）。 */
    public static List<String> businessTableNames() {
        List<String> names = new ArrayList<>();
        for (TableRef ref : businessTableRefs()) {
            names.add(ref.name());
        }
        return Collections.unmodifiableList(names);
    }
}
