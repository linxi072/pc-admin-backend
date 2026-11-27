package db;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 升级脚本存在性守卫回归：全部 upgrade_*.sql 的加列 / 建索引必须经
 * information_schema + PREPARE/EXECUTE 动态 SQL 守卫（非存储过程），可安全重复执行；
 * 不得存在绕过守卫的裸 ALTER TABLE / CREATE UNIQUE INDEX 语句。
 */
class UpgradeScriptGuardDdlTest {

    private static final List<String> SCRIPTS = List.of(
            "upgrade_config_key_unique_index.sql",
            "upgrade_dict_code_unique_index.sql",
            "upgrade_dict_data_value_unique_index.sql",
            "upgrade_form_code_unique_index.sql",
            "upgrade_idempotency_unique_index.sql",
            "upgrade_mask_strategy_code_unique_index.sql",
            "upgrade_notification_template_code_unique_index.sql",
            "upgrade_openapi_client_id_unique_index.sql",
            "upgrade_org_code_unique_index.sql",
            "upgrade_post_code_unique_index.sql",
            "upgrade_role_code_unique_index.sql",
            "upgrade_tenant_code_unique_index.sql",
            "upgrade_user_contact_unique_index.sql",
            "upgrade_user_username_unique_index.sql",
            "upgrade_workflow_process_key_unique_index.sql");

    @Test
    void everyScriptShouldCarryExistenceGuards() throws IOException {
        for (String name : SCRIPTS) {
            String script = resource(name);
            assertTrue(script.contains("SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS")
                            || script.contains("SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS"),
                    name + " 必须至少包含一个存在性守卫块");
            assertTrue(script.contains("PREPARE stmt FROM @ddl;"), name + " 必须经 PREPARE/EXECUTE 执行 DDL");
            assertTrue(script.contains("EXECUTE stmt;"), name + " 必须经 PREPARE/EXECUTE 执行 DDL");
            assertTrue(script.contains("DEALLOCATE PREPARE stmt;"), name + " 必须释放语句句柄");
            assertTrue(script.contains("存在性守卫"), name + " 头部须说明守卫语义");
        }
    }

    @Test
    void noBareAlterOrCreateIndexStatementsShouldRemain() throws IOException {
        for (String name : SCRIPTS) {
            String script = resource(name);
            assertFalse(script.replaceAll("(?m)^    (ALTER TABLE|CREATE UNIQUE INDEX)", "GUARDED")
                            .trim().startsWith("ALTER TABLE"),
                    name + " 不得存在绕过守卫的裸 ALTER TABLE 语句");
            // 行首（非守卫字符串缩进内）不得直接出现 CREATE UNIQUE INDEX 语句
            for (String line : script.split("\n")) {
                if (line.startsWith("CREATE UNIQUE INDEX") || line.startsWith("ALTER TABLE")) {
                    throw new AssertionError(name + " 存在绕过守卫的裸 DDL 语句: " + line);
                }
            }
        }
    }

    @Test
    void guardColumnShouldTargetDeclaredTableAndColumn() throws IOException {
        // 以岗位脚本为样本验证守卫的表/列信息与去重/索引口径一致
        String script = resource("upgrade_post_code_unique_index.sql");
        assertTrue(script.contains("table_name = 'sys_post' AND column_name = 'post_code_active'"),
                "列守卫必须按表名 + 列名精确检查");
        assertTrue(script.contains("table_name = 'sys_post' AND index_name = 'uk_post_tenant_code_active'"),
                "索引守卫必须按表名 + 索引名精确检查");
        // 幂等脚本无加列，仅索引守卫
        String idem = resource("upgrade_idempotency_unique_index.sql");
        assertFalse(idem.contains("@col_exists"), "无加列的脚本不得包含列守卫");
        assertTrue(idem.contains("@idx_exists"), "幂等脚本必须带索引守卫");
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = UpgradeScriptGuardDdlTest.class.getClassLoader().getResourceAsStream("db/init/" + name)) {
            assertNotNull(in, "未找到资源: " + name);
            return new String(in.readAllBytes(), UTF_8);
        }
    }
}
