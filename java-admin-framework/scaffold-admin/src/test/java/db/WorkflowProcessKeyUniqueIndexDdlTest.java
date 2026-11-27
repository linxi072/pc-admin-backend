package db;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** 工作流流程编码唯一索引结构回归：新库 DDL 与存量升级脚本必须同口径。 */
class WorkflowProcessKeyUniqueIndexDdlTest {

    private static final String UNIQUE_INDEX =
            "CREATE UNIQUE INDEX `uk_tenant_key` ON `wf_workflow_design` (`tenant_id`, `process_key_active`)";

    @Test
    void ddlShouldDeclareGeneratedColumnAndUniqueIndex() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        assertTrue(ddl.contains("`process_key_active` VARCHAR(128) GENERATED ALWAYS AS"
                        + " (CASE WHEN `deleted` = 0 THEN `process_key` ELSE NULL END) STORED"),
                "逻辑删除行必须以 NULL 退出唯一键");
        assertTrue(ddl.contains(UNIQUE_INDEX), "新库 DDL 必须包含流程编码唯一索引");
    }

    @Test
    void upgradeScriptShouldAddColumnThenDedupThenCreateIndex() throws IOException {
        String upgrade = resource("db/init/upgrade_workflow_process_key_unique_index.sql");
        int addAt = upgrade.indexOf("ADD COLUMN `process_key_active`");
        int deleteAt = upgrade.indexOf("DELETE r FROM `wf_workflow_design`");
        int createAt = upgrade.indexOf("CREATE UNIQUE INDEX `uk_tenant_key`");
        assertTrue(addAt >= 0 && deleteAt > addAt && createAt > deleteAt,
                "升级顺序必须是：加列 → 去重 → 建唯一索引");
        assertTrue(upgrade.contains("PARTITION BY tenant_id, process_key_active"),
                "去重必须以 (tenant_id, process_key_active) 分组：流程编码为租户内唯一");
        assertTrue(upgrade.contains("ORDER BY version DESC, id DESC"));
        assertTrue(upgrade.contains("WHERE process_key_active IS NOT NULL"));
    }

    @Test
    void uniqueKeyShouldBeTenantScopedAndIgnoreDeletedRows() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        int start = ddl.indexOf("CREATE TABLE `wf_workflow_design`");
        int end = ddl.indexOf("ENGINE=InnoDB", start);
        assertTrue(start >= 0 && end > start);
        String block = ddl.substring(start, end);
        assertTrue(block.contains("`process_key` VARCHAR(128) NOT NULL"));
        assertTrue(block.contains("`deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0"));
        // wf_workflow_design 为业务表：每租户一个 process_key 一条设计行，唯一键含 tenant 维度；
        // 索引名与 WorkflowDesignService#delete 墓碑机制假设的 uk_tenant_key 一致
        String index = ddl.substring(ddl.indexOf(UNIQUE_INDEX));
        assertTrue(index.contains("(`tenant_id`, `process_key_active`)"),
                "唯一索引必须为 (tenant_id, process_key_active) 租户内唯一");
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = WorkflowProcessKeyUniqueIndexDdlTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "未找到资源: " + path);
            return new String(in.readAllBytes(), UTF_8);
        }
    }
}
