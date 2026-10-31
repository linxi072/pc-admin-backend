package com.acme.scaffold.system.tenant;

import com.acme.scaffold.jooq.dsl.TenantTables;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.tenant.domain.SysTenantDO;
import com.acme.scaffold.system.tenant.dto.TenantDataOverview;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户治理服务（M19 正式立项 / 平台跨租户治理通道）。
 *
 * <p>平台管理员在 {@code @TenantIgnorable} 跨租户旁路作用域下调用，对全部业务表执行
 * 跨租户的数据操作（总览 / 归位 / 迁移）。所有写操作仅作用于业务表，平台维表恒不动。</p>
 *
 * <p>本服务不依赖 {@code enforce} 开关：无论隔离是否开启，均通过旁路作用域获得全量可见性，
 * 从而保证「开启隔离后仍可对历史/跨租户数据执行治理」。</p>
 */
@Service
public class TenantGovernanceService {
    private static final JooqTables.TableRef USER = JooqTables.SYS_USER;

    private final DSLContext dsl;

    private static final JooqTables.TableRef TN = JooqTables.SYS_TENANT;

    public TenantGovernanceService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * 跨租户数据总览：遍历每个租户，统计其在各业务表的行数。
     *
     * @return 按租户聚合的行数快照（含各表明细与合计）
     */
    public List<TenantDataOverview> dataOverview() {
        List<SysTenantDO> tenants = JooqWriters.fetchList(dsl, TN,
                SysTenantDO.class, DSL.trueCondition());
        List<TenantDataOverview> result = new ArrayList<>();
        for (SysTenantDO t : tenants) {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (var ref : TenantTables.businessTableRefs()) {
                if (!ref.has("tenant_id")) {
                    continue;
                }
                Condition cond = DSL.and(JooqWriters.notDeleted(ref),
                        ref.field("tenant_id", Long.class).eq(t.getId()));
                long c = JooqWriters.count(dsl, ref, cond);
                counts.put(ref.name(), c);
            }
            result.add(new TenantDataOverview(t.getId(), t.getTenantName(), sum(counts), counts));
        }
        return result;
    }

    /**
     * 存量数据归位：将全部业务表中 {@code tenant_id != targetTenantId} 的行归位到目标租户。
     *
     * @param targetTenantId 目标租户 ID
     * @return 受影响行数合计
     */
    public int backfill(long targetTenantId) {
        int affected = 0;
        for (var ref : TenantTables.businessTableRefs()) {
            if (!ref.has("tenant_id")) {
                continue;
            }
            int n = dsl.update(ref.table())
                    .set(ref.field("tenant_id", Long.class), targetTenantId)
                    .where(ref.field("tenant_id", Long.class).ne(targetTenantId))
                    .execute();
            affected += n;
        }
        return affected;
    }

    /**
     * 跨租户数据迁移：将指定业务表中 {@code tenant_id = fromTenantId} 的行迁移到 {@code toTenantId}。
     *
     * @param fromTenantId 源租户 ID
     * @param toTenantId   目标租户 ID
     * @param tables       限定表名集合（空表示全部业务表）
     * @return 受影响行数合计
     */
    public int reassign(long fromTenantId, long toTenantId, List<String> tables) {
        int affected = 0;
        for (var ref : TenantTables.businessTableRefs()) {
            if (!ref.has("tenant_id")) {
                continue;
            }
            if (!CollectionUtils.isEmpty(tables) && !tables.contains(ref.name())) {
                continue;
            }
            int n = dsl.update(ref.table())
                    .set(ref.field("tenant_id", Long.class), toTenantId)
                    .where(ref.field("tenant_id", Long.class).eq(fromTenantId))
                    .execute();
            affected += n;
        }
        return affected;
    }

    /**
     * 跨租户用户数统计：按 {@code tenant_id} 聚合 {@code sys_user} 行数（仅未删除）。
     *
     * <p>供运营总览展示每个租户的用户规模。本方法在跨租户旁路作用域内执行，
     * 与 {@link #dataOverview()} 同源，不依赖 {@code enforce} 开关。</p>
     *
     * @return key=租户 ID，value=用户数；表无 {@code tenant_id} 或异常时返回空 Map
     */
    public Map<Long, Long> userCountByTenant() {
        if (!USER.has("tenant_id")) {
            return Map.of();
        }
        Field<Long> tenantId = USER.field("tenant_id", Long.class);
        Field<Long> cnt = DSL.field("COUNT(*)", Long.class);
        Map<Long, Long> map = new HashMap<>();
        dsl.select(tenantId, cnt)
                .from(USER.table())
                .where(JooqWriters.notDeleted(USER))
                .groupBy(tenantId)
                .forEach(r -> map.put(r.get(tenantId), r.get(cnt)));
        return map;
    }

    private static long sum(Map<String, Long> counts) {
        long total = 0;
        for (Long v : counts.values()) {
            total += (v == null ? 0 : v);
        }
        return total;
    }
}
