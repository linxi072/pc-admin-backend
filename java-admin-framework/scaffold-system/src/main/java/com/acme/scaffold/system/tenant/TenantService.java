package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.tenant.TenantRules;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.tenant.domain.SysTenantDO;
import com.acme.scaffold.system.tenant.dto.CreateTenantRequest;
import com.acme.scaffold.system.tenant.dto.TenantView;
import com.acme.scaffold.system.tenant.dto.UpdateTenantRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 租户应用服务（V51 租户管理域 / M19 P2）。
 *
 * <p><b>定位</b>：{@code sys_tenant} 是「租户维度表」而非「被隔离数据」——它由平台管理员统一维护，
 * 所有行 {@code tenant_id} 恒为 0（平台租户），本服务查询不按当前租户过滤
 * （P3 的 jOOQ 强制注入会显式跳过本表）。</p>
 *
 * <p><b>校验同源</b>：结构校验全部走 {@link TenantRules} 纯函数（编码/名称格式、状态白名单、
 * 联系人电话/邮箱格式、过期时间须晚于当前），唯一性（tenant_code）由本服务查库裁决。
 * 与 P1 的解析层、P3 的隔离层共用同一份口径，保证「存什么、查什么、按什么隔离」三处一致。</p>
 *
 * <p><b>缓存</b>：租户维度表行数极少且读多写少，{@link #list()} 整表缓存，
 * 写路径（create/update/remove/toggle）统一整缓存驱逐。陈旧仅影响管理页列表，
 * 不影响隔离安全（隔离由 P3 在 SQL 层强制）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    /** 租户列表缓存名（整表维度，单一键）。 */
    private static final String TENANT_LIST_CACHE = "tenantList";

    /** 排序白名单：API 字段名 → 数据库列名（白名单外忽略，防 ORDER BY 注入）。 */
    private static final Map<String, String> SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "tenantCode", "tenant_code",
            "tenantName", "tenant_name",
            "status", "status",
            "createdAt", "created_at");

    private final DSLContext dsl;

    private static final JooqTables.TableRef TN = JooqTables.SYS_TENANT;

    // ------------------------------------------------------------------
    // 管理面查询
    // ------------------------------------------------------------------

    /** 全部租户（含停用，按 id 升序）。整表缓存，见 {@link #list()}。 */
    @Cacheable(cacheNames = TENANT_LIST_CACHE, key = "'all'")
    public List<TenantView> list() {
        List<SysTenantDO> rows = JooqWriters.fetchList(dsl, TN,
                SysTenantDO.class, JooqWriters.notDeleted(TN),
                TN.field("id", Long.class).asc());
        return rows.stream().map(TenantView::of).toList();
    }

    public PageResult<TenantView> page(int page, int size, String tenantCode,
                                       String status, String sortField, String sortDirection) {
        Condition cond = buildCondition(tenantCode, status);
        var query = PageQuery.of(page, size, sortField, sortDirection);
        PageResult<SysTenantDO> raw = JooqWriters.page(dsl, TN,
                SysTenantDO.class, cond, query,
                JooqSorts.resolve(TN, query, SORT_FIELDS,
                        TN.field("id", Long.class).desc()));
        return PageResult.of(raw.page(), raw.size(), raw.total(),
                raw.records().stream().map(TenantView::of).toList());
    }

    public TenantView get(Long id) {
        return TenantView.of(requireById(id));
    }

    // ------------------------------------------------------------------
    // 租户注册 / 治理
    // ------------------------------------------------------------------

    /**
     * 注册租户：结构校验 + tenant_code 唯一性 → 落库（tenant_id 恒为 0）。
     * 返回新记录主键。
     */
    @CacheEvict(cacheNames = TENANT_LIST_CACHE, allEntries = true)
    @Transactional
    public Long create(CreateTenantRequest req) {
        List<String> errors = TenantRules.validateTenant(req.tenantCode(), req.tenantName(),
                req.status(), req.contactPhone(), req.contactEmail(), req.expireAt());
        if (!errors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, String.join("；", errors));
        }
        if (existsByTenantCode(req.tenantCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "租户编码已存在");
        }
        String status = (req.status() != null && !req.status().isBlank())
                ? TenantRules.normalizeTenantStatus(req.status()) : TenantRules.DEFAULT_TENANT_STATUS;
        SysTenantDO d = new SysTenantDO();
        d.setTenantCode(req.tenantCode());
        d.setTenantName(req.tenantName());
        d.setContactName(req.contactName());
        d.setContactPhone(req.contactPhone());
        d.setContactEmail(req.contactEmail());
        d.setStatus(status);
        d.setExpireAt(req.expireAt());
        d.setRemark(req.remark());
        JooqWriters.insert(dsl, TN, d);
        return d.getId();
    }

    /**
     * 更新租户：仅传入的非空字段参与；以「合并后的完整值」做结构校验。
     * tenant_code 不可变更（外部系统已按此绑定）。
     */
    @CacheEvict(cacheNames = TENANT_LIST_CACHE, allEntries = true)
    @Transactional
    public void update(Long id, UpdateTenantRequest req) {
        SysTenantDO existing = requireById(id);
        String code = existing.getTenantCode();
        String name = req.tenantName() != null ? req.tenantName() : existing.getTenantName();
        String contactName = req.contactName() != null ? req.contactName() : existing.getContactName();
        String contactPhone = req.contactPhone() != null ? req.contactPhone() : existing.getContactPhone();
        String contactEmail = req.contactEmail() != null ? req.contactEmail() : existing.getContactEmail();
        String status = req.status() != null ? req.status() : existing.getStatus();
        LocalDateTime expireAt = req.expireAt() != null ? req.expireAt() : existing.getExpireAt();
        String remark = req.remark() != null ? req.remark() : existing.getRemark();
        List<String> errors = TenantRules.validateTenant(code, name, status, contactPhone,
                contactEmail, expireAt);
        if (!errors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, String.join("；", errors));
        }
        SysTenantDO patch = new SysTenantDO();
        if (req.tenantName() != null) {
            patch.setTenantName(req.tenantName());
        }
        if (req.contactName() != null) {
            patch.setContactName(req.contactName());
        }
        if (req.contactPhone() != null) {
            patch.setContactPhone(req.contactPhone());
        }
        if (req.contactEmail() != null) {
            patch.setContactEmail(req.contactEmail());
        }
        if (req.status() != null) {
            patch.setStatus(TenantRules.normalizeTenantStatus(req.status()));
        }
        if (req.expireAt() != null) {
            patch.setExpireAt(req.expireAt());
        }
        if (req.remark() != null) {
            patch.setRemark(req.remark());
        }
        JooqWriters.updateById(dsl, TN, id, patch);
    }

    /** 删除租户（逻辑删除，便于审计与恢复）。 */
    @CacheEvict(cacheNames = TENANT_LIST_CACHE, allEntries = true)
    @Transactional
    public void remove(Long id) {
        requireById(id);
        JooqWriters.delete(dsl, TN, id, true);
    }

    /** 启停租户（ENABLED / DISABLED）。 */
    @CacheEvict(cacheNames = TENANT_LIST_CACHE, allEntries = true)
    @Transactional
    public void toggle(Long id, boolean enabled) {
        requireById(id);
        SysTenantDO patch = new SysTenantDO();
        patch.setStatus(enabled ? "ENABLED" : "DISABLED");
        JooqWriters.updateById(dsl, TN, id, patch);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private SysTenantDO requireById(Long id) {
        SysTenantDO d = JooqWriters.fetchById(dsl, TN, SysTenantDO.class, id);
        if (d == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "租户不存在");
        }
        return d;
    }

    private boolean existsByTenantCode(String tenantCode) {
        return JooqWriters.fetchOne(dsl, TN, SysTenantDO.class,
                DSL.and(JooqWriters.notDeleted(TN),
                        TN.field("tenant_code", String.class).eq(tenantCode))) != null;
    }

    /** 统一筛选条件：逻辑删除恒成立；tenant_code 精确匹配/状态精确匹配按需收敛。 */
    private Condition buildCondition(String tenantCode, String status) {
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(TN));
        if (StringUtils.hasText(tenantCode)) {
            conds.add(TN.field("tenant_code", String.class)
                    .eq(tenantCode.trim()));
        }
        String st = TenantRules.normalizeTenantStatus(status);
        if (st != null) {
            conds.add(TN.field("status", String.class).eq(st));
        }
        return DSL.and(conds);
    }
}
