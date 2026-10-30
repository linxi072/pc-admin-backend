package com.acme.scaffold.system.tenant;

import com.acme.scaffold.jooq.dsl.TenantTables;
import com.acme.scaffold.common.tenant.TenantIgnorable;
import com.acme.scaffold.common.tenant.TenantIsolationTableViolation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 存量隔离自检服务（M45）：扫描全部业务表，统计 tenant_id 未归属（NULL 或 0、且未逻辑删除）的行，
 * 供「安全开启 enforce」前的只读预检。
 *
 * <p><b>只读语义</b>：仅执行 {@code SELECT COUNT(*)}，不写库、不触发任何隔离写入逻辑；
 * 标 {@link TenantIgnorable} 跨租户旁路，确保即使 enforce 已开启也能看到全部行。
 * 表集合复用 {@link TenantTables#businessTableRefs()}（新增业务表自动纳入，杜绝手写清单漂移）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantIsolationAuditService {

    private final DSLContext dsl;

    /**
     * 逐表统计未归属行数。
     *
     * @return 各业务表违规统计；检查失败的表以 {@code violatedRows = -1} 标记，不影响其余表统计
     */
    @TenantIgnorable
    public List<TenantIsolationTableViolation> scanViolations() {
        List<TenantIsolationTableViolation> result = new ArrayList<>();
        for (var ref : TenantTables.businessTableRefs()) {
            Field<Long> tenantId = ref.field("tenant_id", Long.class);
            Condition condition = tenantId.isNull().or(tenantId.eq(0L));
            if (ref.has("deleted")) {
                condition = condition.and(ref.field("deleted", Integer.class).eq(0));
            }
            try {
                long cnt = dsl.fetchCount(ref.table(), condition);
                result.add(new TenantIsolationTableViolation(ref.name(), cnt));
            } catch (DataAccessException e) {
                log.warn("存量隔离自检失败，跳过该表：{}", ref.name(), e);
                result.add(new TenantIsolationTableViolation(ref.name(), -1L));
            }
        }
        return result;
    }
}
