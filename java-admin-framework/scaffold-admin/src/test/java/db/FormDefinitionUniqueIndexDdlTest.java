package db;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** 表单定义编码唯一索引结构回归：新库 DDL 与存量升级脚本必须同口径。 */
class FormDefinitionUniqueIndexDdlTest {

    private static final String UNIQUE_INDEX =
            "CREATE UNIQUE INDEX `uk_form_tenant_code_active` ON `sys_form_definition`"
                    + " (`tenant_id`, `form_code_active`)";

    @Test
    void ddlShouldDeclareGeneratedColumnAndUniqueIndex() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        assertTrue(ddl.contains("`form_code_active` VARCHAR(64) GENERATED ALWAYS AS"
                        + " (CASE WHEN `deleted` = 0 THEN `form_code` ELSE NULL END) STORED"),
                "逻辑删除行必须以 NULL 退出唯一键");
        assertTrue(ddl.contains(UNIQUE_INDEX), "新库 DDL 必须包含表单编码唯一索引");
    }

    @Test
    void upgradeScriptShouldAddColumnThenDedupThenCreateIndex() throws IOException {
        String upgrade = resource("db/init/upgrade_form_code_unique_index.sql");
        int addAt = upgrade.indexOf("ADD COLUMN `form_code_active`");
        int deleteAt = upgrade.indexOf("DELETE r FROM `sys_form_definition`");
        int createAt = upgrade.indexOf("CREATE UNIQUE INDEX `uk_form_tenant_code_active`");
        assertTrue(addAt >= 0 && deleteAt > addAt && createAt > deleteAt,
                "升级顺序必须是：加列 → 去重 → 建唯一索引");
        assertTrue(upgrade.contains("ORDER BY version DESC, id DESC"));
        assertTrue(upgrade.contains("WHERE form_code_active IS NOT NULL"));
    }

    @Test
    void uniqueKeyShouldIgnoreLogicallyDeletedRows() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        int start = ddl.indexOf("CREATE TABLE `sys_form_definition`");
        int end = ddl.indexOf("ENGINE=InnoDB", start);
        assertTrue(start >= 0 && end > start);
        String block = ddl.substring(start, end);
        assertTrue(block.contains("`tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0"));
        assertTrue(block.contains("ELSE NULL END"));
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = FormDefinitionUniqueIndexDdlTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "未找到资源: " + path);
            return new String(in.readAllBytes(), UTF_8);
        }
    }
}
