package db;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** 字典值唯一索引结构回归：新库 DDL 与存量升级脚本必须同口径。 */
class DictDataValueUniqueIndexDdlTest {

    private static final String UNIQUE_INDEX =
            "CREATE UNIQUE INDEX `uk_dict_tenant_type_value_active` ON `sys_dict_data` (`tenant_id`, `dict_type_code`, `dict_value_active`)";

    @Test
    void ddlShouldDeclareGeneratedColumnAndUniqueIndex() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        assertTrue(ddl.contains("`dict_value_active` VARCHAR(100) GENERATED ALWAYS AS"
                        + " (CASE WHEN `deleted` = 0 THEN `dict_value` ELSE NULL END) STORED"),
                "逻辑删除行必须以 NULL 退出唯一键");
        assertTrue(ddl.contains(UNIQUE_INDEX), "新库 DDL 必须包含字典值唯一索引");
    }

    @Test
    void upgradeScriptShouldAddColumnThenDedupThenCreateIndex() throws IOException {
        String upgrade = resource("db/init/upgrade_dict_data_value_unique_index.sql");
        int addAt = upgrade.indexOf("ADD COLUMN `dict_value_active`");
        int deleteAt = upgrade.indexOf("DELETE r FROM `sys_dict_data`");
        int createAt = upgrade.indexOf("CREATE UNIQUE INDEX `uk_dict_tenant_type_value_active`");
        assertTrue(addAt >= 0 && deleteAt > addAt && createAt > deleteAt,
                "升级顺序必须是：加列 → 去重 → 建唯一索引");
        assertTrue(upgrade.contains("PARTITION BY tenant_id, dict_type_code, dict_value_active"),
                "去重必须以 (tenant_id, dict_type_code, dict_value_active) 分组：字典值为类型内唯一");
        assertTrue(upgrade.contains("ORDER BY version DESC, id DESC"));
        assertTrue(upgrade.contains("WHERE dict_value_active IS NOT NULL"));
    }

    @Test
    void uniqueKeyShouldBeTenantTypeScopedAndIgnoreDeletedRows() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        int start = ddl.indexOf("CREATE TABLE `sys_dict_data`");
        int end = ddl.indexOf("ENGINE=InnoDB", start);
        assertTrue(start >= 0 && end > start);
        String block = ddl.substring(start, end);
        assertTrue(block.contains("`dict_value` VARCHAR(100) NOT NULL"));
        assertTrue(block.contains("`dict_type_code` VARCHAR(64) NOT NULL"));
        assertTrue(block.contains("ELSE NULL END"));
        // sys_dict_data 为业务表：同一租户同一字典类型下 dict_value 唯一，唯一键含 tenant + type 维度
        String index = ddl.substring(ddl.indexOf(UNIQUE_INDEX));
        assertTrue(index.contains("(`tenant_id`, `dict_type_code`, `dict_value_active`)"),
                "唯一索引必须为 (tenant_id, dict_type_code, dict_value_active) 三元组键");
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = DictDataValueUniqueIndexDdlTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "未找到资源: " + path);
            return new String(in.readAllBytes(), UTF_8);
        }
    }
}
