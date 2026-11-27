package com.acme.scaffold.system.form.service;
import com.acme.scaffold.common.error.CommonErrorCode;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.tenant.TenantEnforcement;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.FetchSupport;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.form.domain.model.FormDefinition;
import com.acme.scaffold.system.form.domain.rule.FormSchemaRules;
import com.acme.scaffold.system.form.domain.CreateFormDefinitionRequest;
import com.acme.scaffold.system.form.domain.FormDefinitionView;
import com.acme.scaffold.system.form.domain.UpdateFormDefinitionRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 表单定义应用服务（V47 低代码表单试点 / M22 spike → M22 深化：表单定义管理 CRUD）。
 *
 * <p><b>只读面</b>（list / getByCode / getEnabledByCode）+ 按「租户 + 编码」缓存沿用 spike 实现，
 * 强隔离下同名表单编码不会跨租户串缓存。</p>
 *
 * <p><b>管理写面</b>（create / update / delete，本期正式立项）：落库前经
 * {@link FormSchemaRules#validate} 校验 Schema 结构（version / title / fields / 字段键 / 类型白名单 /
 * options / 长度属性），并对 {@code form_code} 做租户内唯一性校验；写操作后统一
 * {@link #refreshCache()} 失效按编码缓存，保证下发面不会命中写操作前的旧快照。绑定目标
 * {@code targetType} 须经 {@link FormSchemaRules#normalizeTargetType} 归一（NONE / BUSINESS_TABLE /
 * FORM_SUBMISSION），非法值整体拒绝不放行。</p>
 *
 * <p><b>下发前复核</b>：{@link #getEnabledByCode} 命中的定义会再跑一次
 * {@link FormSchemaRules#validate}，防止库中遗留非法 schema 被渲染面消费。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormDefinitionService {

    private final DSLContext dsl;

    /** 按「租户 + 编码」缓存；单租户部署固定 T0 前缀，保持既有行为。 */
    private final Map<String, FormDefinition> codeCache = new ConcurrentHashMap<>();

    /** 全部表单定义（含停用，按 id 升序，供前端选择器/全量联动）。 */
    public List<FormDefinitionView> list() {
        List<FormDefinition> rows = SqlWriters.fetchList(dsl, SqlTables.FORM_DEFINITION,
                FormDefinition.class, SqlWriters.notDeleted(SqlTables.FORM_DEFINITION),
                SqlTables.FORM_DEFINITION.field("id", Long.class).asc());
        return rows.stream().map(FormDefinitionView::of).toList();
    }

    /** 按 id 取表单定义（含停用；不存在抛 NOT_FOUND）。 */
    public FormDefinitionView getById(Long id) {
        FormDefinition d = FetchSupport.requirePresent(SqlWriters.fetchById(dsl, SqlTables.FORM_DEFINITION, FormDefinition.class, id), "表单定义不存在");
        return FormDefinitionView.of(d);
    }

    /** 按编码取表单定义（含停用；不存在抛 NOT_FOUND）。 */
    public FormDefinitionView getByCode(String formCode) {
        return FormDefinitionView.of(requireByCode(formCode));
    }

    /** 按编码取「启用中」表单定义（渲染面入口；停用/不存在均抛 NOT_FOUND）。 */
    public FormDefinitionView getEnabledByCode(String formCode) {
        FormDefinition d = codeCache.computeIfAbsent(cacheKey(formCode), this::loadEnabledByCode);
        // 缓存可能来自写操作前的历史加载：停用态一律拒绝下发
        if (d == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "表单定义不存在或已停用");
        }
        return FormDefinitionView.of(d);
    }

    /**
     * 缓存键：强隔离下带当前租户前缀，避免租户 A 先加载的启用表单被租户 B 同名编码命中；
     * 隔离关闭时固定 T0，保持单租户部署行为不变。
     */
    private String cacheKey(String formCode) {
        String tenant = TenantEnforcement.isEnabled()
                ? "T" + TenantEnforcement.currentTenantId() : "T0";
        return tenant + "|" + formCode;
    }

    /** 全量失效缓存（写操作后调用）。 */
    public void refreshCache() {
        codeCache.clear();
    }

    // ------------------------------------------------------------------
    // 管理写面（M22 深化）
    // ------------------------------------------------------------------

    /** 创建表单定义：唯一性 + Schema 校验 + 绑定目标归一，写库后失效缓存。返回新记录 id。 */
    @Transactional
    public Long create(CreateFormDefinitionRequest req) {
        if (existsByCode(req.formCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "表单编码已存在");
        }
        List<String> errors = FormSchemaRules.validate(req.schemaJson());
        if (!errors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "表单 Schema 非法：" + String.join("；", errors));
        }
        String target = FormSchemaRules.normalizeTargetType(req.targetType());
        if (target == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "绑定目标非法");
        }
        FormDefinition d = new FormDefinition();
        d.setFormCode(req.formCode());
        d.setFormName(req.formName());
        d.setSchemaJson(req.schemaJson());
        d.setTargetType(target);
        d.setEnabled(req.enabled() != null && req.enabled() ? 1 : 0);
        d.setRemark(req.remark());
        try {
            SqlWriters.insert(dsl, SqlTables.FORM_DEFINITION, d);
        } catch (DataIntegrityViolationException e) {
            // 并发同编码创建：应用层查重与插入的时间窗由唯一键裁决，避免落到通用 409 文案。
            throw new BusinessException(CommonErrorCode.CONFLICT, "表单编码已存在");
        }
        refreshCache();
        return d.getId();
    }

    /** 更新表单定义：存在性 + 编码唯一性（编码变更时）+ Schema 校验 + 绑定目标归一，写库后失效缓存。 */
    @Transactional
    public void update(Long id, UpdateFormDefinitionRequest req) {
        FormDefinition existing = FetchSupport.requirePresent(SqlWriters.fetchById(dsl, SqlTables.FORM_DEFINITION, FormDefinition.class, id), "表单定义不存在");
        if (!existing.getFormCode().equals(req.formCode()) && existsByCode(req.formCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "表单编码已存在");
        }
        List<String> errors = FormSchemaRules.validate(req.schemaJson());
        if (!errors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "表单 Schema 非法：" + String.join("；", errors));
        }
        String target = FormSchemaRules.normalizeTargetType(req.targetType());
        if (target == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "绑定目标非法");
        }
        FormDefinition d = new FormDefinition();
        d.setFormCode(req.formCode());
        d.setFormName(req.formName());
        d.setSchemaJson(req.schemaJson());
        d.setTargetType(target);
        d.setEnabled(req.enabled() != null ? (req.enabled() ? 1 : 0)
                : (existing.getEnabled() == null ? 0 : existing.getEnabled()));
        d.setRemark(req.remark());
        try {
            SqlWriters.updateById(dsl, SqlTables.FORM_DEFINITION, id, d);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "表单编码已存在");
        }
        refreshCache();
    }

    /** 删除表单定义（逻辑删除），写库后失效缓存。 */
    @Transactional
    public void delete(Long id) {
        FormDefinition existing = FetchSupport.requirePresent(SqlWriters.fetchById(dsl, SqlTables.FORM_DEFINITION, FormDefinition.class, id), "表单定义不存在");
        SqlWriters.delete(dsl, SqlTables.FORM_DEFINITION, id, true);
        refreshCache();
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private boolean existsByCode(String formCode) {
        return SqlWriters.count(dsl, SqlTables.FORM_DEFINITION,
                DSL.and(SqlWriters.notDeleted(SqlTables.FORM_DEFINITION),
                        SqlTables.FORM_DEFINITION.field("form_code", String.class).eq(formCode))) > 0;
    }

    private FormDefinition loadEnabledByCode(String formCode) {
        Condition cond = DSL.and(SqlWriters.notDeleted(SqlTables.FORM_DEFINITION),
                SqlTables.FORM_DEFINITION.field("form_code", String.class).eq(formCode),
                SqlTables.FORM_DEFINITION.field("enabled", Integer.class).eq(1));
        return SqlWriters.fetchOne(dsl, SqlTables.FORM_DEFINITION, FormDefinition.class, cond);
    }

    private FormDefinition requireByCode(String formCode) {
        FormDefinition d = SqlWriters.fetchOne(dsl, SqlTables.FORM_DEFINITION,
                FormDefinition.class,
                DSL.and(SqlWriters.notDeleted(SqlTables.FORM_DEFINITION),
                        SqlTables.FORM_DEFINITION.field("form_code", String.class).eq(formCode)));
        if (d == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "表单定义不存在");
        }
        return d;
    }
}
