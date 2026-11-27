package db;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** 字典编码唯一索引结构回归：新库 DDL 与存量升级脚本必须同口径。 */
class DictCodeUniqueIndexDdlTest {

    private static final String UNIQUE_INDEX =
            "CREATE UNIQUE INDEX `uk_dict_code_active` ON `sys_dict_type` (`dict_code_active`)";

    @Test
    void ddlShouldDeclareGeneratedColumnAndUniqueIndex() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        assertTrue(ddl.contains("`dict_code_active` VARCHAR(64) GENERATED ALWAYS AS"
                        + " (CASE WHEN `deleted` = 0 THEN `dict_code` ELSE NULL END) STORED"),
                "逻辑删除行必须以 NULL 退出唯一键");
        assertTrue(ddl.contains(UNIQUE_INDEX), "新库 DDL 必须包含字典编码唯一索引");
    }

    @Test
    void upgradeScriptShouldAddColumnThenDedupThenCreateIndex() throws IOException {
        String upgrade = resource("db/init/upgrade_dict_code_unique_index.sql");
        int addAt = upgrade.indexOf("ADD COLUMN `dict_code_active`");
        int deleteAt = upgrade.indexOf("DELETE r FROM `sys_dict_type`");
        int createAt = upgrade.indexOf("CREATE UNIQUE INDEX `uk_dict_code_active`");
        assertTrue(addAt >= 0 && deleteAt > addAt && createAt > deleteAt,
                "升级顺序必须是：加列 → 去重 → 建唯一索引");
        assertTrue(upgrade.contains("ORDER BY version DESC, id DESC"));
        assertTrue(upgrade.contains("WHERE dict_code_active IS NOT NULL"));
    }

    @Test
    void uniqueKeyShouldBeGlobalAndIgnoreDeletedRows() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        int start = ddl.indexOf("CREATE TABLE `sys_dict_type`");
        int end = ddl.indexOf("ENGINE=InnoDB", start);
        assertTrue(start >= 0 && end > start);
        String block = ddl.substring(start, end);
        assertTrue(block.contains("`dict_code` VARCHAR(64) NOT NULL"));
        assertTrue(block.contains("ELSE NULL END"));
        // sys_dict_type 为平台维表（租户豁免隔离）：唯一键为全局 dict_code_active，不含 tenant 维度
        String index = ddl.substring(ddl.indexOf(UNIQUE_INDEX));
        assertTrue(index.contains("(`dict_code_active`)"),
                "唯一索引必须为全局单列键（不含 tenant_id）");
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = DictCodeUniqueIndexDdlTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "未找到资源: " + path);
            return new String(in.readAllBytes(), UTF_8);
        }
    }
}
