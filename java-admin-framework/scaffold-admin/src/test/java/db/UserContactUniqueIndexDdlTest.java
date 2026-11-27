package db;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** 用户手机号 / 邮箱唯一索引结构回归：新库 DDL 与存量升级脚本必须同口径。 */
class UserContactUniqueIndexDdlTest {

    private static final String MOBILE_ACTIVE =
            "`mobile_active` VARCHAR(32) GENERATED ALWAYS AS"
                    + " (CASE WHEN `deleted` = 0 THEN `mobile` ELSE NULL END) STORED";
    private static final String EMAIL_ACTIVE =
            "`email_active` VARCHAR(128) GENERATED ALWAYS AS"
                    + " (CASE WHEN `deleted` = 0 THEN `email` ELSE NULL END) STORED";
    private static final String MOBILE_INDEX =
            "CREATE UNIQUE INDEX `uk_user_tenant_mobile_active` ON `sys_user`"
                    + " (`tenant_id`, `mobile_active`)";
    private static final String EMAIL_INDEX =
            "CREATE UNIQUE INDEX `uk_user_tenant_email_active` ON `sys_user`"
                    + " (`tenant_id`, `email_active`)";

    @Test
    void ddlShouldDeclareGeneratedColumnsAndUniqueIndexes() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        assertTrue(ddl.contains(MOBILE_ACTIVE), "手机号生成列必须以 NULL 退出唯一键");
        assertTrue(ddl.contains(EMAIL_ACTIVE), "邮箱生成列必须以 NULL 退出唯一键");
        assertTrue(ddl.contains(MOBILE_INDEX), "新库 DDL 必须包含手机号唯一索引");
        assertTrue(ddl.contains(EMAIL_INDEX), "新库 DDL 必须包含邮箱唯一索引");
    }

    @Test
    void upgradeScriptShouldAddColumnsThenDedupThenCreateIndexes() throws IOException {
        String upgrade = resource("db/init/upgrade_user_contact_unique_index.sql");
        int addAt = upgrade.indexOf("ADD COLUMN `mobile_active`");
        int addEmailAt = upgrade.indexOf("ADD COLUMN `email_active`");
        int delMobileAt = upgrade.indexOf("DELETE r FROM `sys_user`");
        int delEmailAt = upgrade.lastIndexOf("DELETE r FROM `sys_user`");
        int createMobileAt = upgrade.indexOf("CREATE UNIQUE INDEX `uk_user_tenant_mobile_active`");
        int createEmailAt = upgrade.indexOf("CREATE UNIQUE INDEX `uk_user_tenant_email_active`");
        assertTrue(addAt >= 0 && addEmailAt > addAt && delMobileAt > addEmailAt
                        && delEmailAt > delMobileAt && createMobileAt > delEmailAt && createEmailAt > createMobileAt,
                "升级顺序必须是：加列 → 手机号去重 → 邮箱去重 → 建两个唯一索引");
        assertTrue(upgrade.contains("ORDER BY version DESC, id DESC"));
        assertTrue(upgrade.contains("WHERE mobile_active IS NOT NULL"));
        assertTrue(upgrade.contains("WHERE email_active IS NOT NULL"));
    }

    @Test
    void uniqueKeysShouldIgnoreUnboundAndDeletedRows() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        int start = ddl.indexOf("CREATE TABLE `sys_user`");
        int end = ddl.indexOf("ENGINE=InnoDB", start);
        assertTrue(start >= 0 && end > start);
        String block = ddl.substring(start, end);
        assertTrue(block.contains("`mobile` VARCHAR(32) NULL"), "手机号必须允许 NULL（未绑定）");
        assertTrue(block.contains("`email` VARCHAR(128) NULL"), "邮箱必须允许 NULL（未绑定）");
        assertTrue(block.contains("ELSE NULL END"), "生成列必须把未绑定/逻辑删除行退化为 NULL");
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = UserContactUniqueIndexDdlTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "未找到资源: " + path);
            return new String(in.readAllBytes(), UTF_8);
        }
    }
}
