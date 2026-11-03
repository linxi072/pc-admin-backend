package com.acme.scaffold.config.multiregion;

import com.acme.scaffold.config.datasource.DataSourceRegistry;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 按部署单元独立迁移（R4）。
 *
 * <p>多区域下每个单元（具名数据源）是独立的物理库，须各自执行 Flyway 迁移，
 * 而非仅对主库执行一次。本组件对 {@link DataSourceRegistry} 中的<b>每一个</b>数据源
 * 执行一遍迁移脚本（默认 locations = spring.flyway.locations）。</p>
 *
 * <p><b>与 Spring Boot 自带 Flyway 自动装配的关系</b>：启用多区域后，应设
 * {@code spring.flyway.enabled=false}，改由本组件统一对所有单元（含主库）迁移，
 * 避免主库被重复迁移。本组件自身仅当多区域启用时才作为 Bean 存在（条件化装配），零回归。</p>
 */
public class UnitAwareFlywayMigrator {

    private static final Logger log = LoggerFactory.getLogger(UnitAwareFlywayMigrator.class);

    private final DataSourceRegistry registry;
    private final List<String> locations;

    public UnitAwareFlywayMigrator(DataSourceRegistry registry, List<String> locations) {
        this.registry = registry;
        this.locations = (locations == null || locations.isEmpty())
                ? List.of("classpath:db/migration") : locations;
    }

    /** 对每个注册的数据源执行 Flyway 迁移，返回「单元名 → 迁移结果」摘要。 */
    public MigrationReport migrateAll() {
        MigrationReport report = new MigrationReport();
        for (String name : registry.names()) {
            DataSource ds = registry.get(name);
            try {
                Flyway flyway = Flyway.configure()
                        .dataSource(ds)
                        .locations(locations.toArray(new String[0]))
                        .load();
                flyway.migrate();
                report.record(name, true, null);
                log.info("[multiregion] 单元 {} Flyway 迁移完成（locations={}）", name, locations);
            } catch (Exception e) {
                report.record(name, false, e.getMessage());
                log.error("[multiregion] 单元 {} Flyway 迁移失败: {}", name, e.getMessage());
                throw new IllegalStateException("多区域单元 " + name + " 迁移失败: " + e.getMessage(), e);
            }
        }
        return report;
    }

    /** 迁移结果摘要（单元名 + 成功标记 + 失败原因）。 */
    public static class MigrationReport {
        private final List<Entry> entries = new ArrayList<>();

        void record(String unit, boolean success, String error) {
            entries.add(new Entry(unit, success, error));
        }

        public List<Entry> entries() {
            return entries;
        }

        public int successCount() {
            int n = 0;
            for (Entry e : entries) {
                if (e.success()) {
                    n++;
                }
            }
            return n;
        }

        public record Entry(String unit, boolean success, String error) {
        }
    }
}
