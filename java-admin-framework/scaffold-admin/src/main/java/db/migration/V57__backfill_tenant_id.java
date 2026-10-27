package db.migration;

import com.acme.scaffold.jooq.dsl.TenantTables;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * 存量业务表 {@code tenant_id} 回填（M19 SaaS 开通 / 生产启用闭环）。
 *
 * <p><b>动机</b>：M19 隔离落地（V51）为业务表新增 {@code tenant_id} 列（默认 0）。
 * 历史数据或显式写入 NULL 的行在开启强制隔离后会被 {@code JooqWriters} 误判为平台租户（0），
 * 故在部署期一次性把全部业务表的 NULL 归位到平台租户（0），与既有单租户行为零回归。</p>
 *
 * <p><b>数据驱动</b>：以运行时 {@code information_schema} 为权威，逐业务表探测是否真实存在
 * {@code tenant_id} 列，仅对确实含该列的业务表执行回填。不依赖 {@code TableRef} 列清单的维护，
 * 即便个别业务表在 Java 侧未登记列名也不会漏填。</p>
 *
 * <p><b>幂等</b>：仅处理 {@code tenant_id IS NULL} 的行，已有合法值（含 0）的行不受影响；
 * 可重复执行，每次仅回填仍残留的 NULL，单事务由 Flyway 托管。</p>
 *
 * <p><b>零孤儿断言</b>：回填后遍历业务表校验不存在 {@code tenant_id IS NULL} 的行，
 * 否则中断启动（fail-fast），杜绝「列已加但值为 NULL」造成的隔离泄漏。</p>
 *
 * <p><b>作用面</b>：复用 {@link TenantTables#businessTableNames()}（M19 业务表权威清单），
 * 自动覆盖所有业务表，新增业务表无需手工维护此迁移；平台维表（sys_tenant/sys_menu/…）恒不触碰。</p>
 */
public class V57__backfill_tenant_id extends BaseJavaMigration {

    /** 平台 / 遗留租户 ID（与 {@code TenantRules.PLATFORM_TENANT_ID} 一致）。 */
    private static final long PLATFORM_TENANT_ID = 0L;

    /** 待回填的业务表（运行时确认含 tenant_id 列者）。 */
    private final List<String> targets = new ArrayList<>();

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        String schema = currentSchema(conn);
        int totalBackfilled = 0;

        // 1) 探测：仅纳入「业务表 + 确实含 tenant_id 列」的表
        for (String table : TenantTables.businessTableNames()) {
            if (hasTenantIdColumn(conn, schema, table)) {
                targets.add(table);
            }
        }

        // 2) 回填：tenant_id IS NULL -> 平台租户（0）
        for (String table : targets) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE " + table + " SET tenant_id = ? WHERE tenant_id IS NULL")) {
                ps.setLong(1, PLATFORM_TENANT_ID);
                totalBackfilled += ps.executeUpdate();
            }
        }

        // 3) 零孤儿断言：回填后不得残留 tenant_id IS NULL 的业务行
        for (String table : targets) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(1) FROM " + table + " WHERE tenant_id IS NULL");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getLong(1) > 0) {
                    throw new IllegalStateException(
                            "存量 tenant_id 回填后仍存在 NULL 孤儿行（表 " + table
                                    + "，行数 " + rs.getLong(1) + "）：请检查表结构或迁移执行顺序");
                }
            }
        }

        if (totalBackfilled > 0) {
            org.slf4j.LoggerFactory.getLogger(V57__backfill_tenant_id.class)
                    .info("存量 tenant_id 回填完成：共 {} 行归位到平台租户({})，涉及 {} 张业务表",
                            totalBackfilled, PLATFORM_TENANT_ID, targets.size());
        }
    }

    private static String currentSchema(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT DATABASE()");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        return null;
    }

    private static boolean hasTenantIdColumn(Connection conn, String schema, String table) throws Exception {
        String sql = "SELECT 1 FROM information_schema.columns "
                + "WHERE column_name = 'tenant_id' AND table_name = ?"
                + (schema != null ? " AND table_schema = ?" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            if (schema != null) {
                ps.setString(2, schema);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
