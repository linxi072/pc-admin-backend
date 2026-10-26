package com.acme.scaffold.common.tenant;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.TenantTables;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TenantTables 分类正确性自测（纯函数，无 DB）。
 */
class TenantTablesTest {

    @Test
    @DisplayName("平台维表被正确识别为豁免隔离表")
    void platformTablesRecognized() {
        for (String t : TenantTables.PLATFORM_TABLES) {
            assertTrue(TenantTables.isPlatformTable(t), "应为平台维表: " + t);
        }
        assertFalse(TenantTables.isPlatformTable("sys_user"));
        assertFalse(TenantTables.isPlatformTable("sys_role"));
    }

    @Test
    @DisplayName("业务表集合非空，且不含任何平台维表")
    void businessTablesExcludePlatform() {
        List<JooqTables.TableRef> biz = TenantTables.businessTableRefs();
        assertNotNull(biz);
        assertFalse(biz.isEmpty(), "业务表集合不应为空");

        // 已知业务表须在内
        List<String> names = TenantTables.businessTableNames();
        assertTrue(names.contains("sys_user"));
        assertTrue(names.contains("sys_role"));
        assertTrue(names.contains("sys_org"));

        // 平台维表绝不应出现在业务表集合中
        for (String p : TenantTables.PLATFORM_TABLES) {
            assertFalse(names.contains(p), "平台维表不应出现在业务表集合: " + p);
        }
    }
}
