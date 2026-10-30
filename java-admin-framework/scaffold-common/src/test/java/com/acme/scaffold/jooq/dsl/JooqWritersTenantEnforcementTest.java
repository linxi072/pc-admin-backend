package com.acme.scaffold.jooq.dsl;

import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.common.tenant.TenantEnforcement;
import com.acme.scaffold.common.tenant.TenantProperties;
import com.acme.scaffold.jooq.dsl.JooqTables.TableRef;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.ParamType;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M19 P3 强制注入隔离的端到端自测（Docker-free）。
 *
 * <p>通过 jOOQ {@link MockConnection} 在内存中「执行」SQL 并捕获渲染后的语句与绑定值，
 * 验证 {@link JooqWriters} 在 {@code enforce=true} 时确实向读/改/删/写路径注入 {@code tenant_id}，
 * 且平台维表豁免、非请求线程与旁路作用域安全 no-op。无需真实数据库或 Testcontainers。</p>
 */
class JooqWritersTenantEnforcementTest {

    private static final long CURRENT_TENANT = 42L;

    /** 业务表：需隔离，含 tenant_id 列。 */
    private final TableRef bizTable = new TableRef("biz_order", "id", "tenant_id", "order_no", "deleted");
    /** 平台维表：豁免隔离（位于默认 platformTables）。 */
    private final TableRef platformTable = JooqTables.SYS_TENANT;

    private final List<Executed> executed = new ArrayList<>();

    private static final class Executed {
        final String sql;
        final Object[] bindings;

        Executed(String sql, Object[] bindings) {
            this.sql = sql;
            this.bindings = bindings != null ? bindings : new Object[0];
        }

        boolean sqlContains(String frag) {
            return sql.toLowerCase().contains(frag.toLowerCase());
        }

        boolean binds(Long v) {
            for (Object b : bindings) {
                if (Objects.equals(b, v)) {
                    return true;
                }
            }
            return false;
        }
    }

    private DSLContext dsl;

    @BeforeEach
    void init() {
        MockDataProvider provider = new MockDataProvider() {
            @Override
            public MockResult[] execute(MockExecuteContext ctx) {
                String sql = ctx.sql();
                Object[] bindings = ctx.bindings();
                executed.add(new Executed(sql != null ? sql : "", bindings));
                // 仅捕获渲染后的 SQL 与绑定值以验证租户注入；返回 0 行 MockResult 即可。
                return new MockResult[]{ new MockResult(0) };
            }
        };
        Connection conn = new MockConnection(provider);
        dsl = DSL.using(conn, SQLDialect.MYSQL, new Settings().withParamType(ParamType.INDEXED));
    }

    @AfterEach
    void reset() {
        TenantContext.clear();
        TenantEnforcement.configure(null);
        executed.clear();
    }

    /** 配置强制隔离开关，并选择性写入当前租户作用域。 */
    private void configure(boolean enforce, boolean setTenant) {
        TenantProperties props = new TenantProperties();
        props.setEnforce(enforce);
        TenantEnforcement.configure(props);
        if (setTenant) {
            TenantContext.setTenantId(CURRENT_TENANT);
        } else {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("enforce=false：读业务表不追加 tenant_id 过滤（零回归）")
    void readNoEnforce() {
        configure(false, true);
        JooqWriters.fetchList(dsl, bizTable, OrderPojo.class, DSL.trueCondition());
        Executed e = lastSelect();
        assertFalse(e.binds(CURRENT_TENANT), "enforce=false 时不应注入 tenant_id 绑定");
    }

    @Test
    @DisplayName("enforce=true 且已设租户：读业务表追加 tenant_id = 当前租户")
    void readEnforcedAppendsTenant() {
        configure(true, true);
        JooqWriters.fetchList(dsl, bizTable, OrderPojo.class, DSL.trueCondition());
        Executed e = lastSelect();
        assertTrue(e.sqlContains("tenant_id"), "WHERE 应追加 tenant_id 过滤");
        assertTrue(e.binds(CURRENT_TENANT), "tenant_id 绑定值应为当前租户 42");
    }

    @Test
    @DisplayName("enforce=true 且已设租户：按 id 删除追加 tenant_id 过滤")
    void deleteEnforcedAppendsTenant() {
        configure(true, true);
        JooqWriters.delete(dsl, bizTable, 7L, false);
        Executed e = lastDelete();
        assertTrue(e.sqlContains("tenant_id"), "DELETE WHERE 应追加 tenant_id 过滤");
        assertTrue(e.binds(CURRENT_TENANT), "tenant_id 绑定值应为当前租户 42");
    }

    @Test
    @DisplayName("enforce=true 且已设租户：插入业务表写入 tenant_id = 当前租户")
    void insertEnforcedWritesTenant() {
        configure(true, true);
        JooqWriters.insert(dsl, bizTable, new OrderPojo("ORD-1"));
        Executed e = lastInsert();
        assertTrue(e.binds(CURRENT_TENANT), "INSERT 应将 tenant_id 绑定为当前租户 42");
    }

    @Test
    @DisplayName("enforce=true 且已设租户：平台维表(sys_tenant)读豁免隔离")
    void platformTableReadExempt() {
        configure(true, true);
        JooqWriters.fetchList(dsl, platformTable, OrderPojo.class, DSL.trueCondition());
        Executed e = lastSelect();
        assertFalse(e.binds(CURRENT_TENANT), "平台维表读不应追加 tenant_id 绑定");
    }

    @Test
    @DisplayName("enforce=true 且已设租户：平台维表插入回落平台租户 0")
    void platformTableInsertFallsToPlatform() {
        configure(true, true);
        JooqWriters.insert(dsl, platformTable, new OrderPojo("X"));
        Executed e = lastInsert();
        assertTrue(e.binds(0L), "平台维表插入 tenant_id 应为平台租户 0");
        assertFalse(e.binds(CURRENT_TENANT), "平台维表插入不应绑定 42");
    }

    @Test
    @DisplayName("enforce=true 但未设租户作用域：读业务表不强制（非请求线程安全 no-op）")
    void enforcedButNoScopeNoInjection() {
        configure(true, false);
        JooqWriters.fetchList(dsl, bizTable, OrderPojo.class, DSL.trueCondition());
        Executed e = lastSelect();
        assertFalse(e.binds(CURRENT_TENANT), "未设作用域时不应注入 tenant_id");
    }

    @Test
    @DisplayName("enforce=true 且处于旁路：读业务表不强制（@TenantIgnorable 跨租户通道）")
    void bypassScopeNoInjection() {
        configure(true, true);
        TenantContext.runWithBypass(() -> {
            JooqWriters.fetchList(dsl, bizTable, OrderPojo.class, DSL.trueCondition());
            return null;
        });
        Executed e = lastSelect();
        assertFalse(e.binds(CURRENT_TENANT), "旁路作用域不应注入 tenant_id");
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private Executed lastSelect() {
        for (int i = executed.size() - 1; i >= 0; i--) {
            if (executed.get(i).sql.toLowerCase().contains("select")) {
                return executed.get(i);
            }
        }
        throw new AssertionError("无 SELECT 执行记录");
    }

    private Executed lastInsert() {
        for (int i = executed.size() - 1; i >= 0; i--) {
            if (executed.get(i).sql.toLowerCase().contains("insert")) {
                return executed.get(i);
            }
        }
        throw new AssertionError("无 INSERT 执行记录");
    }

    private Executed lastDelete() {
        for (int i = executed.size() - 1; i >= 0; i--) {
            if (executed.get(i).sql.toLowerCase().contains("delete")) {
                return executed.get(i);
            }
        }
        throw new AssertionError("无 DELETE 执行记录");
    }

    /** 最小 POJO：供 JooqWriters 反射读写（id 回填空，orderNo 非空属性）。 */
    public static class OrderPojo {
        private Long id;
        private String orderNo;

        public OrderPojo() {
        }

        public OrderPojo(String orderNo) {
            this.orderNo = orderNo;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }
    }
}
