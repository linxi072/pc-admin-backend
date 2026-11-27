package com.acme.scaffold.system.maskstrategy.service;
import com.acme.scaffold.common.api.domain.PageQuery;
import com.acme.scaffold.common.api.domain.PageResult;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.sensitive.SensitiveMasker;
import com.acme.scaffold.common.sensitive.SensitiveType;
import com.acme.scaffold.common.util.MergeFields;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.FetchSupport;
import com.acme.scaffold.mybatis.dsl.SqlSorts;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.maskstrategy.domain.model.SysMaskStrategy;
import com.acme.scaffold.system.maskstrategy.domain.rule.MaskStrategyRules;
import com.acme.scaffold.system.maskstrategy.domain.CreateMaskStrategyRequest;
import com.acme.scaffold.system.maskstrategy.domain.MaskStrategyView;
import com.acme.scaffold.system.maskstrategy.domain.UpdateMaskStrategyRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 脱敏策略中心应用服务（V46 脱敏策略中心）。
 *
 * <p><b>双数据源</b>：管理面（page/list/get）直接查库，覆盖全部状态（含停用）；解析面
 * （{@link #activeStrategies}）走按租户缓存的「启用中」策略列表，供 {@link MaskStrategyResolver}
 * 在序列化/导出前高频命中，避免每次请求回查数据库。任一写操作后立即全量失效缓存
 * （平台策略会被所有租户继承，只清单租户会留下旧策略残留）。</p>
 *
 * <p><b>默认安全回落</b>：解析面未命中策略时，{@code MaskStrategyResolver} 按调用方声明的
 * {@code SensitiveType} 默认掩码——与既有视图层脱敏口径一致，绝不因「未配置策略」而泄漏明文。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaskStrategyService {

    /** 排序白名单：客户端字段名 → 数据库列名（白名单外忽略，防 ORDER BY 注入）。 */
    private static final Map<String, String> SORT_FIELDS = SqlSorts.whitelist(
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

    private final Map<Long, List<SysMaskStrategy>> activeCache = new ConcurrentHashMap<>();
    private final AtomicLong cacheVersion = new AtomicLong(0);

    /**
     * 取某租户「启用中」的策略列表（按租户缓存，首次访问惰性加载）。
     * 解析面只关心启用项，停用项不进入命中集合。
     */
    public List<SysMaskStrategy> activeStrategies(long tenantId) {
        return activeCache.computeIfAbsent(tenantId, this::loadActive);
    }

    private List<SysMaskStrategy> loadActive(long tenantId) {
        Condition cond = DSL.and(SqlWriters.notDeleted(SqlTables.MASK_STRATEGY),
                SqlTables.MASK_STRATEGY.field("enabled", Integer.class).eq(1),
                SqlTables.MASK_STRATEGY.field("tenant_id", Long.class).eq(tenantId));
        return SqlWriters.fetchList(dsl, SqlTables.MASK_STRATEGY, SysMaskStrategy.class, cond);
    }

    /**
     * 全量失效（写操作后与管理端点主动触发均调用）。
     *
     * <p>必须全清而非只清当前租户：平台租户 0 的策略会被所有业务租户继承，
     * 仅失效租户 0 会让其他租户缓存继续持有旧平台策略；同理，租户侧策略也可能由跨租户管理操作变更。
     * 写操作频率远低于解析命中，全清是最稳妥且实现最简单的口径。</p>
     */
    public long refreshCache() {
        activeCache.clear();
        return cacheVersion.incrementAndGet();
    }

    /**
     * 脱敏效果预览：复用 {@link SensitiveMasker#mask} 单一数据源，避免前端重写掩码逻辑导致口径漂移。
     *
     * <p>纯计算、不落库、不读缓存；非法脱敏类型直接拒绝（400），由调用方（编辑弹窗）即时修正。</p>
     *
     * @param sensitiveType 脱敏类型（枚举名，大小写不敏感；null/非法拒绝）
     * @param sampleValue    待脱敏示例值（可为空，空值原样返回）
     * @return 脱敏后的示例值
     */
    public String previewMask(String sensitiveType, String sampleValue) {
        SensitiveType type = MaskStrategyRules.parseSensitiveType(sensitiveType);
        if (type == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "脱敏类型非法");
        }
        return SensitiveMasker.mask(sampleValue, type);
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
        PageResult<SysMaskStrategy> raw = SqlWriters.page(dsl, SqlTables.MASK_STRATEGY,
                SysMaskStrategy.class, cond, query,
                SqlSorts.resolve(SqlTables.MASK_STRATEGY, query, SORT_FIELDS,
                        SqlTables.MASK_STRATEGY.field("priority", Integer.class).desc(),
                        SqlTables.MASK_STRATEGY.field("id", Long.class).desc()));
        return PageResult.of(raw.page(), raw.size(), raw.total(),
                raw.records().stream().map(MaskStrategyView::of).toList());
    }

    /** 全部策略（含停用，按 id 升序，供前端下拉/全量联动）。 */
    public List<MaskStrategyView> list() {
        List<SysMaskStrategy> rows = SqlWriters.fetchList(dsl, SqlTables.MASK_STRATEGY,
                SysMaskStrategy.class, SqlWriters.notDeleted(SqlTables.MASK_STRATEGY),
                SqlTables.MASK_STRATEGY.field("id", Long.class).asc());
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
        SysMaskStrategy d = new SysMaskStrategy();
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
        try {
            SqlWriters.insert(dsl, SqlTables.MASK_STRATEGY, d);
        } catch (DataIntegrityViolationException e) {
            // 并发同编码创建：应用层查重与插入的时间窗由唯一键 uk_mask_strategy_code_active
            // 兜底裁决，转换为与预校验一致的清晰 409。
            throw new BusinessException(CommonErrorCode.CONFLICT, "策略编码已存在");
        }
        refreshCache();
        return d.getId();
    }

    /**
     * 更新策略：仅传入的非空字段参与更新；以「合并后的完整值」做结构校验，
     * 编码变更时再做唯一性校验，保证部分更新后仍满足策略契约。
     */
    public void update(Long id, UpdateMaskStrategyRequest req) {
        SysMaskStrategy existing = requireById(id);
        String code = MergeFields.orKeep(req::strategyCode, existing::getStrategyCode);
        String name = MergeFields.orKeep(req::strategyName, existing::getStrategyName);
        String fieldKey = MergeFields.orKeep(req::fieldKey, existing::getFieldKey);
        String type = MergeFields.orKeep(req::sensitiveType, existing::getSensitiveType);
        String scope = MergeFields.orKeep(req::applyScope, existing::getApplyScope);
        String exempt = MergeFields.orKeep(req::exemptRoles, existing::getExemptRoles);
        Integer priority = MergeFields.orKeep(req::priority, existing::getPriority);
        List<String> errors = MaskStrategyRules.validate(
                code, name, fieldKey, type, scope, exempt, priority);
        if (!errors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, String.join("；", errors));
        }
        if (req.strategyCode() != null && !req.strategyCode().equals(existing.getStrategyCode())
                && existsByCode(req.strategyCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "策略编码已存在");
        }
        SysMaskStrategy patch = new SysMaskStrategy();
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
        try {
            SqlWriters.updateById(dsl, SqlTables.MASK_STRATEGY, id, patch);
        } catch (DataIntegrityViolationException e) {
            // 并发改码 / 并发同码创建：唯一键 uk_mask_strategy_code_active 兜底裁决，
            // 转换为与预校验一致的清晰 409（update 允许改写 strategy_code，冲突面含更新路径）。
            throw new BusinessException(CommonErrorCode.CONFLICT, "策略编码已存在");
        }
        refreshCache();
    }

    /** 删除策略（逻辑删除，便于审计与恢复）。 */
    public void delete(Long id) {
        requireById(id);
        SqlWriters.delete(dsl, SqlTables.MASK_STRATEGY, id, true);
        refreshCache();
    }

    /** 启停策略。 */
    public void toggle(Long id, boolean enabled) {
        requireById(id);
        SysMaskStrategy patch = new SysMaskStrategy();
        patch.setEnabled(enabled ? 1 : 0);
        SqlWriters.updateById(dsl, SqlTables.MASK_STRATEGY, id, patch);
        refreshCache();
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private SysMaskStrategy requireById(Long id) {
        SysMaskStrategy d = FetchSupport.requirePresent(SqlWriters.fetchById(dsl, SqlTables.MASK_STRATEGY, SysMaskStrategy.class, id), "脱敏策略不存在");
        return d;
    }

    private boolean existsByCode(String code) {
        return fetchByCode(code) != null;
    }

    private SysMaskStrategy fetchByCode(String code) {
        return SqlWriters.fetchOne(dsl, SqlTables.MASK_STRATEGY, SysMaskStrategy.class,
                DSL.and(SqlWriters.notDeleted(SqlTables.MASK_STRATEGY),
                        SqlTables.MASK_STRATEGY.field("strategy_code", String.class).eq(code)));
    }

    /** 统一筛选条件：逻辑删除恒成立；字段键/脱敏类型/启用状态按需收敛。 */
    private Condition buildCondition(String fieldKey, String sensitiveType, Boolean enabled) {
        List<Condition> conds = new ArrayList<>();
        conds.add(SqlWriters.notDeleted(SqlTables.MASK_STRATEGY));
        if (StringUtils.hasText(fieldKey)) {
            conds.add(SqlTables.MASK_STRATEGY.field("field_key", String.class).eq(fieldKey.trim()));
        }
        String st = MaskStrategyRules.normalizeSensitiveType(sensitiveType);
        if (st != null) {
            conds.add(SqlTables.MASK_STRATEGY.field("sensitive_type", String.class).eq(st));
        }
        if (enabled != null) {
            conds.add(SqlTables.MASK_STRATEGY.field("enabled", Integer.class).eq(enabled ? 1 : 0));
        }
        return DSL.and(conds);
    }
}
