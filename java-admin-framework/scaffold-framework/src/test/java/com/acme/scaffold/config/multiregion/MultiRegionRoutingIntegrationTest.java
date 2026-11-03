package com.acme.scaffold.config.multiregion;

import com.acme.scaffold.config.datasource.DataSourceContextHolder;
import com.acme.scaffold.config.datasource.RoutingDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多区域路由集成测试（R4 验收核心）：用两个 H2 内存库模拟「两个部署单元（物理库）」，
 * 经真实的 {@link RoutingDataSource} + {@link DataSourceContextHolder} 验证——
 * 设置路由上下文后，经主 DSLContext 的写入只落在对应单元的物理库，实现租户组物理隔离。
 */
class MultiRegionRoutingIntegrationTest {

    private DataSource dsA;
    private DataSource dsB;
    private RoutingDataSource routing;
    private DSLContext dsl;

    @BeforeEach
    void setUp() throws Exception {
        dsA = h2("unit_a");
        dsB = h2("unit_b");
        createTable(dsA);
        createTable(dsB);

        RoutingDataSource rds = new RoutingDataSource();
        Map<Object, Object> targets = new LinkedHashMap<>();
        targets.put("unit-a", dsA);
        targets.put("unit-b", dsB);
        rds.setTargetDataSources(targets);
        rds.setDefaultTargetDataSource(dsA);
        rds.setLenientFallback(true);
        rds.afterPropertiesSet();
        routing = rds;
        dsl = DSL.using(routing, SQLDialect.H2);
    }

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
    }

    @Test
    void tenantInUnitA_writesToUnitADbOnly() {
        DataSourceContextHolder.set("unit-a");
        insertRow(1L, 10L, "a");

        assertTrue(rowExists(dsA, 1L), "unit-a 库应含该租户行");
        assertFalse(rowExists(dsB, 1L), "unit-b 库不应含该租户行（物理隔离）");
    }

    @Test
    void tenantInUnitB_writesToUnitBDbOnly() {
        DataSourceContextHolder.set("unit-b");
        insertRow(2L, 20L, "b");

        assertTrue(rowExists(dsB, 2L), "unit-b 库应含该租户行");
        assertFalse(rowExists(dsA, 2L), "unit-a 库不应含该租户行（物理隔离）");
    }

    @Test
    void multiRegionRouter_routesByTenantGroup() {
        // 租户 10 未命中映射 -> 默认 unit-a；租户 20 显式归属 unit-b
        TenantUnitMapping mapping = TenantUnitMapping.create("unit-a",
                Map.of(20L, "unit-b"), List.of());
        MultiRegionRouter router = new MultiRegionRouter(mapping);

        router.runWithTenant(10L, () -> insertRow(10L, 10L, "ra"));
        router.runWithTenant(20L, () -> insertRow(20L, 20L, "rb"));

        assertTrue(rowExists(dsA, 10L));
        assertFalse(rowExists(dsB, 10L));
        assertTrue(rowExists(dsB, 20L));
        assertFalse(rowExists(dsA, 20L));
    }

    private void insertRow(long id, long tenantId, String payload) {
        dsl.insertInto(DSL.table("mr_demo"))
                .columns(DSL.field("id", Long.class),
                        DSL.field("tenant_id", Long.class),
                        DSL.field("payload", String.class))
                .values(id, tenantId, payload)
                .execute();
    }

    private static DataSource h2(String name) {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + "_" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private static void createTable(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE mr_demo (id BIGINT PRIMARY KEY, tenant_id BIGINT, payload VARCHAR(50))");
        }
    }

    private static boolean rowExists(DataSource ds, long id) {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            var rs = st.executeQuery("SELECT COUNT(*) FROM mr_demo WHERE id = " + id);
            rs.next();
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
