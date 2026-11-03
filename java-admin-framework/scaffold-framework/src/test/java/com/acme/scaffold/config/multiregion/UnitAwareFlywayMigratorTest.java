package com.acme.scaffold.config.multiregion;

import com.acme.scaffold.config.datasource.DataSourceRegistry;
import com.acme.scaffold.config.datasource.MultiDataSourceProperties;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UnitAwareFlywayMigrator} 集成测试：用两个 H2 内存库模拟「两个部署单元」，
 * 验证 migrateAll 对注册表中的每个数据源都执行迁移（而非仅主库），证明按单元独立迁移。
 */
class UnitAwareFlywayMigratorTest {

    @Test
    void migrateAll_runsOnEveryUnitDatasource() {
        MultiDataSourceProperties props = new MultiDataSourceProperties();
        props.setPrimary("unit-a");
        props.setEntries(buildEntries());
        DataSourceRegistry registry = new DataSourceRegistry(props);

        UnitAwareFlywayMigrator migrator = new UnitAwareFlywayMigrator(
                registry, List.of("classpath:db/multiregion-test"));
        UnitAwareFlywayMigrator.MigrationReport report = migrator.migrateAll();

        assertEquals(2, report.entries().size(), "两个单元都应进入迁移报告");
        assertEquals(2, report.successCount(), "两个单元都应迁移成功");
        assertTrue(tableExists(registry.get("unit-a"), "mr_region_test"), "unit-a 库应已建表");
        assertTrue(tableExists(registry.get("unit-b"), "mr_region_test"), "unit-b 库应已建表");
    }

    private Map<String, MultiDataSourceProperties.DataSourceProperties> buildEntries() {
        Map<String, MultiDataSourceProperties.DataSourceProperties> map = new LinkedHashMap<>();
        map.put("unit-a", h2Entry("mr_mig_a"));
        map.put("unit-b", h2Entry("mr_mig_b"));
        return map;
    }

    private MultiDataSourceProperties.DataSourceProperties h2Entry(String db) {
        MultiDataSourceProperties.DataSourceProperties p = new MultiDataSourceProperties.DataSourceProperties();
        p.setDriverClassName("org.h2.Driver");
        p.setUrl("jdbc:h2:mem:" + db + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        p.setUsername("sa");
        p.setPassword("");
        return p;
    }

    private boolean tableExists(DataSource ds, String table) {
        try (Connection c = ds.getConnection();
             var rs = c.getMetaData().getTables(null, null, table.toLowerCase(), new String[]{"TABLE"})) {
            return rs.next();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
