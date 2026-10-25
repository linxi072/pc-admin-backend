package com.acme.scaffold.system.maskstrategy;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.maskstrategy.domain.SysMaskStrategyDO;
import com.acme.scaffold.system.maskstrategy.dto.CreateMaskStrategyRequest;
import com.acme.scaffold.system.maskstrategy.dto.MaskStrategyView;
import com.acme.scaffold.system.maskstrategy.dto.UpdateMaskStrategyRequest;
import com.acme.scaffold.system.maskstrategy.rules.MaskStrategyRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 脱敏策略中心应用服务（V46 脱敏策略中心）。
 *
 * <p><b>双数据源</b>：管理面（page/list/get）直接查库，覆盖全部状态（含停用）；解析面
 * （{@link #activeStrategies}）走按租户缓存的「启用中」策略列表，供 {@link MaskStrategyResolver}
 * 在序列化/导出前高频命中，避免每次请求回查数据库。任一写操作后立即失效对应租户缓存。</p>
 *
 * <p><b>默认安全回落</b>：解析面未命中策略时，{@code MaskStrategyResolver} 按调用方声明的
 * {@code SensitiveType} 默认掩码——与既有视图层脱敏口径一致，绝不因「未配置策略」而泄漏明文。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaskStrategyService {

    /** 排序白名单：客户端字段名 → 数据库列名（白名单外忽略，防 ORDER BY 注入）。 */
    private static final Map<String, String> SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "strategyCode", "strategy_code",
            "strategyName", "strategy_name",
            "fieldKey", "field_key",
            "sensitiveType", "sensitive_type",
            "priority", "priority",
            "enabled", "enabled",
            "createdAt", "created_at");

    private final DSLContext dsl;

    // ------------------------------------------------------------------
    // 解析面缓存（按租户）：启用中策略列表。写操作即时失效。
    // ------------------------------------------------------------------

    private final Map<Long, List<SysMaskStrategyDO>> activeCache = new ConcurrentHashMap<>();
    private final AtomicLong cacheVersion = new AtomicLong(0);

    /**
     * 取某租户「启用中」的策略列表（按租户缓存，首次访问惰性加载）。
     * 解析面只关心启用项，停用项不进入命中集合。
     */
    public List<SysMaskStrategyDO> activeStrategies(long tenantId) {
        return activeCache.computeIfAbsent(tenantId, this::loadActive);
    }

    private List<SysMaskStrategyDO> loadActive(long tenantId) {
        Condition cond = DSL.and(JooqWriters.notDeleted(JooqTables.MASK_STRATEGY),
                JooqTables.MASK_STRATEGY.field("enabled", Integer.class).eq(1),
                JooqTables.MASK_STRATEGY.field("tenant_id", Long.class).eq(tenantId));
        return JooqWriters.fetchList(dsl, JooqTables.MASK_STRATEGY, SysMaskStrategyDO.class, cond);
    }

    /** 失效某租户缓存（写操作后调用）。 */
    private void invalidate(long tenantId) {
        activeCache.remove(tenantId);
        cacheVersion.incrementAndGet();
    }

    /** 全量失效（管理端点主动触发，如跨租户批量变更后）。 */
    public long refreshCache() {
        activeCache.clear();
        return cacheVersion.incrementAndGet();
    }

    /** 当前缓存版本（便于观测与测试）。 */
    public long cacheVersion() {
        return cacheVersion.get();
    }

    // ------------------------------------------------------------------
    // 管理面查询
    // ------------------------------------------------------------------

    public PageResult<MaskStrategyView> page(int page, int size, String fieldKey,
                                            String sensitiveType, Boolean enabled,
                                            String sortField, String sortDirection) {
        Condition cond = buildCondition(fieldKey, sensitiveType, enabled);
        var query = PageQuery.of(page, size, sortField, sortDirection);
        PageResult<SysMaskStrategyDO> raw = JooqWriters.page(dsl, JooqTables.MASK_STRATEGY,
                SysMaskStrategyDO.class, cond, query,
                JooqSorts.resolve(JooqTables.MASK_STRATEGY, query, SORT_FIELDS,
                        JooqTables.MASK_STRATEGY.field("priority", Integer.class).desc(),
                        JooqTables.MASK_STRATEGY.field("id", Long.class).desc()));
        return PageResult.of(raw.page(), raw.size(), raw.total(),
                raw.records().stream().map(MaskStrategyView::of).toList());
    }

    /** 全部策略（含停用，按 id 升序，供前端下拉/全量联动）。 */
    public List<MaskStrategyView> list() {
        List<SysMaskStrategyDO> rows = JooqWriters.fetchList(dsl, JooqTables.MASK_STRATEGY,
                SysMaskStrategyDO.class, JooqWriters.notDeleted(JooqTables.MASK_STRATEGY),
                JooqTables.MASK_STRATEGY.field("id", Long.class).asc());
        return rows.stream().map(MaskStrategyView::of).toList();
    }

    public MaskStrategyView get(Long id) {
        return MaskStrategyView.of(requireById(id));
    }

    // ------------------------------------------------------------------
    // 写操作
    // ------------------------------------------------------------------

    /** 创建策略：结构校验 + 编码唯一性校验，通过后落库并失效缓存。 */
    public Long create(CreateMaskStrategyRequest req) {
        List<String> errors = MaskStrategyRules.validate(req.strategyCode(), req.strategyName(),
                req.fieldKey(), req.sensitiveType(), req.applyScope(), req.exemptRoles(), req.priority());
        if (!errors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, String.join("；", errors));
        }
        if (existsByCode(req.strategyCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "策略编码已存在");
        }
        SysMaskStrategyDO d = new SysMaskStrategyDO();
        d.setStrategyCode(req.strategyCode());
        d.setStrategyName(req.strategyName());
        d.setFieldKey(req.fieldKey());
        d.setSensitiveType(MaskStrategyRules.normalizeSensitiveType(req.sensitiveType()));
        d.setApplyScope(MaskStrategyRules.normalizeScope(req.applyScope()) == null
                ? MaskStrategyRules.DEFAULT_SCOPE : MaskStrategyRules.normalizeScope(req.applyScope()));
        d.setExemptRoles(req.exemptRoles());
        d.setPriority(req.priority() != null ? req.priority() : 0);
        d.setEnabled(req.enabled() != null && req.enabled() ? 1 : 0);
        d.setRemark(req.remark());
        JooqWriters.insert(dsl, JooqTables.MASK_STRATEGY, d);
        invalidate(0L);
        return d.getId();
    }

    /**
     * 更新策略：仅传入的非空字段参与更新；以「合并后的完整值」做结构校验，
     * 编码变更时再做唯一性校验，保证部分更新后仍满足策略契约。
     */
    public void update(Long id, UpdateMaskStrategyRequest req) {
        SysMaskStrategyDO existing = requireById(id);
        String code = req.strategyCode() != null ? req.strategyCode() : existing.getStrategyCode();
        String name = req.strategyName() != null ? req.strategyName() : existing.getStrategyName();
        String fieldKey = req.fieldKey() != null ? req.fieldKey() : existing.getFieldKey();
        String type = req.sensitiveType() != null ? req.sensitiveType() : existing.getSensitiveType();
        String scope = req.applyScope() != null ? req.applyScope() : existing.getApplyScope();
        String exempt = req.exemptRoles() != null ? req.exemptRoles() : existing.getExemptRoles();
        Integer priority = req.priority() != null ? req.priority() : existing.getPriority();
        List<String> errors = MaskStrategyRules.validate(
                code, name, fieldKey, type, scope, exempt, priority);
        if (!errors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, String.join("；", errors));
        }
        if (req.strategyCode() != null && !req.strategyCode().equals(existing.getStrategyCode())
                && existsByCode(req.strategyCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "策略编码已存在");
        }
        SysMaskStrategyDO patch = new SysMaskStrategyDO();
        if (req.strategyCode() != null) {
            patch.setStrategyCode(req.strategyCode());
        }
        if (req.strategyName() != null) {
            patch.setStrategyName(req.strategyName());
        }
        if (req.fieldKey() != null) {
            patch.setFieldKey(req.fieldKey());
        }
        if (req.sensitiveType() != null) {
            patch.setSensitiveType(MaskStrategyRules.normalizeSensitiveType(req.sensitiveType()));
        }
        if (req.applyScope() != null) {
            patch.setApplyScope(MaskStrategyRules.normalizeScope(req.applyScope()));
        }
        if (req.exemptRoles() != null) {
            patch.setExemptRoles(req.exemptRoles());
        }
        if (req.priority() != null) {
            patch.setPriority(req.priority());
        }
        if (req.enabled() != null) {
            patch.setEnabled(req.enabled() ? 1 : 0);
        }
        if (req.remark() != null) {
            patch.setRemark(req.remark());
        }
        JooqWriters.updateById(dsl, JooqTables.MASK_STRATEGY, id, patch);
        invalidate(0L);
    }

    /** 删除策略（逻辑删除，便于审计与恢复）。 */
    public void remove(Long id) {
        requireById(id);
        JooqWriters.delete(dsl, JooqTables.MASK_STRATEGY, id, true);
        invalidate(0L);
    }

    /** 启停策略。 */
    public void toggle(Long id, boolean enabled) {
        requireById(id);
        SysMaskStrategyDO patch = new SysMaskStrategyDO();
        patch.setEnabled(enabled ? 1 : 0);
        JooqWriters.updateById(dsl, JooqTables.MASK_STRATEGY, id, patch);
        invalidate(0L);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private SysMaskStrategyDO requireById(Long id) {
        SysMaskStrategyDO d = JooqWriters.fetchById(dsl, JooqTables.MASK_STRATEGY,
                SysMaskStrategyDO.class, id);
        if (d == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "脱敏策略不存在");
        }
        return d;
    }

    private boolean existsByCode(String code) {
        return fetchByCode(code) != null;
    }

    private SysMaskStrategyDO fetchByCode(String code) {
        return JooqWriters.fetchOne(dsl, JooqTables.MASK_STRATEGY, SysMaskStrategyDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.MASK_STRATEGY),
                        JooqTables.MASK_STRATEGY.field("strategy_code", String.class).eq(code)));
    }

    /** 统一筛选条件：逻辑删除恒成立；字段键/脱敏类型/启用状态按需收敛。 */
    private Condition buildCondition(String fieldKey, String sensitiveType, Boolean enabled) {
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(JooqTables.MASK_STRATEGY));
        if (StringUtils.hasText(fieldKey)) {
            conds.add(JooqTables.MASK_STRATEGY.field("field_key", String.class).eq(fieldKey.trim()));
        }
        String st = MaskStrategyRules.normalizeSensitiveType(sensitiveType);
        if (st != null) {
            conds.add(JooqTables.MASK_STRATEGY.field("sensitive_type", String.class).eq(st));
        }
        if (enabled != null) {
            conds.add(JooqTables.MASK_STRATEGY.field("enabled", Integer.class).eq(enabled ? 1 : 0));
        }
        return DSL.and(conds);
    }
}
