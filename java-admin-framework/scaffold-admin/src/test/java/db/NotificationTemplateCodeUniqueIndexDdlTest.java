package db;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** 通知模板编码唯一索引结构回归：新库 DDL 与存量升级脚本必须同口径。 */
class NotificationTemplateCodeUniqueIndexDdlTest {

    private static final String UNIQUE_INDEX =
            "CREATE UNIQUE INDEX `uk_notification_template_code_active` ON `sys_notification_template` (`tenant_id`, `template_code_active`)";

    @Test
    void ddlShouldDeclareGeneratedColumnAndUniqueIndex() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        assertTrue(ddl.contains("`template_code_active` VARCHAR(64) GENERATED ALWAYS AS"
                        + " (CASE WHEN `deleted` = 0 THEN `template_code` ELSE NULL END) STORED"),
                "逻辑删除行必须以 NULL 退出唯一键");
        assertTrue(ddl.contains(UNIQUE_INDEX), "新库 DDL 必须包含模板编码唯一索引");
    }

    @Test
    void upgradeScriptShouldAddColumnThenDedupThenCreateIndex() throws IOException {
        String upgrade = resource("db/init/upgrade_notification_template_code_unique_index.sql");
        int addAt = upgrade.indexOf("ADD COLUMN `template_code_active`");
        int deleteAt = upgrade.indexOf("DELETE r FROM `sys_notification_template`");
        int createAt = upgrade.indexOf("CREATE UNIQUE INDEX `uk_notification_template_code_active`");
        assertTrue(addAt >= 0 && deleteAt > addAt && createAt > deleteAt,
                "升级顺序必须是：加列 → 去重 → 建唯一索引");
        assertTrue(upgrade.contains("PARTITION BY tenant_id, template_code_active"),
                "去重必须以 (tenant_id, template_code_active) 分组：模板编码为租户内唯一");
        assertTrue(upgrade.contains("ORDER BY version DESC, id DESC"));
        assertTrue(upgrade.contains("WHERE template_code_active IS NOT NULL"));
    }

    @Test
    void uniqueKeyShouldBeTenantScopedAndIgnoreDeletedRows() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        int start = ddl.indexOf("CREATE TABLE `sys_notification_template`");
        int end = ddl.indexOf("ENGINE=InnoDB", start);
        assertTrue(start >= 0 && end > start);
        String block = ddl.substring(start, end);
        assertTrue(block.contains("`template_code` VARCHAR(64) NOT NULL"));
        assertTrue(block.contains("ELSE NULL END"));
        // sys_notification_template 为业务表：发送侧按 template_code 经租户隔离取模板，唯一键含 tenant 维度
        String index = ddl.substring(ddl.indexOf(UNIQUE_INDEX));
        assertTrue(index.contains("(`tenant_id`, `template_code_active`)"),
                "唯一索引必须为 (tenant_id, template_code_active) 租户内唯一");
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = NotificationTemplateCodeUniqueIndexDdlTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "未找到资源: " + path);
            return new String(in.readAllBytes(), UTF_8);
        }
    }
}
