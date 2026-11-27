package db;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** 开放平台客户端 ID 唯一索引结构回归：新库 DDL 与存量升级脚本必须同口径。 */
class OpenApiClientIdUniqueIndexDdlTest {

    private static final String UNIQUE_INDEX =
            "CREATE UNIQUE INDEX `uk_openapi_client_id_active` ON `sys_openapi_client` (`client_id_active`)";

    @Test
    void ddlShouldDeclareGeneratedColumnAndUniqueIndex() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        assertTrue(ddl.contains("`client_id_active` VARCHAR(64) GENERATED ALWAYS AS"
                        + " (CASE WHEN `deleted` = 0 THEN `client_id` ELSE NULL END) STORED"),
                "逻辑删除行必须以 NULL 退出唯一键");
        assertTrue(ddl.contains(UNIQUE_INDEX), "新库 DDL 必须包含客户端 ID 唯一索引");
    }

    @Test
    void upgradeScriptShouldAddColumnThenDedupThenCreateIndex() throws IOException {
        String upgrade = resource("db/init/upgrade_openapi_client_id_unique_index.sql");
        int addAt = upgrade.indexOf("ADD COLUMN `client_id_active`");
        int deleteAt = upgrade.indexOf("DELETE r FROM `sys_openapi_client`");
        int createAt = upgrade.indexOf("CREATE UNIQUE INDEX `uk_openapi_client_id_active`");
        assertTrue(addAt >= 0 && deleteAt > addAt && createAt > deleteAt,
                "升级顺序必须是：加列 → 去重 → 建唯一索引");
        assertTrue(upgrade.contains("ORDER BY version DESC, id DESC"));
        assertTrue(upgrade.contains("WHERE client_id_active IS NOT NULL"));
    }

    @Test
    void uniqueKeyShouldBeGlobalAndIgnoreDeletedRows() throws IOException {
        String ddl = resource("db/init/ddl.sql");
        int start = ddl.indexOf("CREATE TABLE `sys_openapi_client`");
        int end = ddl.indexOf("ENGINE=InnoDB", start);
        assertTrue(start >= 0 && end > start);
        String block = ddl.substring(start, end);
        assertTrue(block.contains("`client_id` VARCHAR(64) NOT NULL"));
        assertTrue(block.contains("ELSE NULL END"));
        // OAuth2 授权服务器按 client_id 无租户维度全局认证：唯一键为全局单列，不含 tenant 维度
        String index = ddl.substring(ddl.indexOf(UNIQUE_INDEX));
        assertTrue(index.contains("(`client_id_active`)"),
                "唯一索引必须为全局单列键（不含 tenant_id）");
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = OpenApiClientIdUniqueIndexDdlTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "未找到资源: " + path);
            return new String(in.readAllBytes(), UTF_8);
        }
    }
}
