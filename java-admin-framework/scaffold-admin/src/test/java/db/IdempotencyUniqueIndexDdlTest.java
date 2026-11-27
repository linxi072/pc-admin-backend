package db;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** 幂等唯一索引结构回归：新库 DDL 与存量库升级脚本必须提供同一唯一键口径。 */
class IdempotencyUniqueIndexDdlTest {

    private static final String UNIQUE_INDEX =
            "CREATE UNIQUE INDEX `uk_idem_tenant_scope_key` ON `sys_idempotency_record`"
                    + " (`tenant_id`, `api_scope`, `idempotency_key`)";

    @Test
    void ddlShouldDeclareUniqueTenantScopeKey() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        assertTrue(ddl.contains(UNIQUE_INDEX), "新库 DDL 必须包含幂等唯一索引");
    }

    @Test
    void idempotencyTableShouldDefineKeyColumns() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        int start = ddl.indexOf("CREATE TABLE `sys_idempotency_record`");
        int end = ddl.indexOf("ENGINE=InnoDB", start);
        assertTrue(start >= 0 && end > start);
        String block = ddl.substring(start, end);
        assertTrue(block.contains("`tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0"));
        assertTrue(block.contains("`api_scope` VARCHAR(128) NOT NULL"));
        assertTrue(block.contains("`idempotency_key` VARCHAR(128) NOT NULL"));
    }

    @Test
    void upgradeScriptShouldDeduplicateBeforeCreatingIndex() throws IOException {
        String upgrade = resource("db/init/upgrade_idempotency_unique_index.sql");
        int deleteAt = upgrade.indexOf("DELETE r FROM `sys_idempotency_record`");
        int createAt = upgrade.indexOf("CREATE UNIQUE INDEX `uk_idem_tenant_scope_key`");
        assertTrue(deleteAt >= 0, "存量库升级脚本必须先去重");
        assertTrue(createAt > deleteAt, "去重必须发生在建唯一索引之前");
        assertTrue(upgrade.contains("ROW_NUMBER() OVER"));
        assertTrue(upgrade.contains("WHEN 'SUCCESS' THEN 0"));
        assertTrue(upgrade.contains("(`tenant_id`, `api_scope`, `idempotency_key`)"));
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = IdempotencyUniqueIndexDdlTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "未找到资源: " + path);
            return new String(in.readAllBytes(), UTF_8);
        }
    }
}
