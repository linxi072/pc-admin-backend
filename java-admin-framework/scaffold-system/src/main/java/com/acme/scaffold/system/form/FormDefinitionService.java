package com.acme.scaffold.system.form;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.form.domain.FormDefinitionDO;
import com.acme.scaffold.system.form.dto.CreateFormDefinitionRequest;
import com.acme.scaffold.system.form.dto.FormDefinitionView;
import com.acme.scaffold.system.form.dto.UpdateFormDefinitionRequest;
import com.acme.scaffold.system.form.rules.FormSchemaRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表单定义应用服务（V47 低代码表单试点 / M22 spike → M22 深化：表单定义管理 CRUD）。
 *
 * <p><b>只读面</b>（list / getByCode / getEnabledByCode）+ 按编码缓存沿用 spike 实现。</p>
 *
 * <p><b>管理写面</b>（create / update / remove，本期正式立项）：落库前经
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

    /** 按编码缓存（spike 单租户 tenant=0 口径，键为 form_code）。 */
    private final Map<String, FormDefinitionDO> codeCache = new ConcurrentHashMap<>();

    /** 全部表单定义（含停用，按 id 升序，供前端选择器/全量联动）。 */
    public List<FormDefinitionView> list() {
        List<FormDefinitionDO> rows = JooqWriters.fetchList(dsl, JooqTables.FORM_DEFINITION,
                FormDefinitionDO.class, JooqWriters.notDeleted(JooqTables.FORM_DEFINITION),
                JooqTables.FORM_DEFINITION.field("id", Long.class).asc());
        return rows.stream().map(FormDefinitionView::of).toList();
    }

    /** 按 id 取表单定义（含停用；不存在抛 NOT_FOUND）。 */
    public FormDefinitionView getById(Long id) {
        FormDefinitionDO d = JooqWriters.fetchById(dsl, JooqTables.FORM_DEFINITION, FormDefinitionDO.class, id);
        if (d == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "表单定义不存在");
        }
        return FormDefinitionView.of(d);
    }

    /** 按编码取表单定义（含停用；不存在抛 NOT_FOUND）。 */
    public FormDefinitionView getByCode(String formCode) {
        return FormDefinitionView.of(requireByCode(formCode));
    }

    /** 按编码取「启用中」表单定义（渲染面入口；停用/不存在均抛 NOT_FOUND）。 */
    public FormDefinitionView getEnabledByCode(String formCode) {
        FormDefinitionDO d = codeCache.computeIfAbsent(formCode, this::loadEnabledByCode);
        // 缓存可能来自写操作前的历史加载：停用态一律拒绝下发
        if (d == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "表单定义不存在或已停用");
        }
        return FormDefinitionView.of(d);
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
        FormDefinitionDO d = new FormDefinitionDO();
        d.setFormCode(req.formCode());
        d.setFormName(req.formName());
        d.setSchemaJson(req.schemaJson());
        d.setTargetType(target);
        d.setEnabled(req.enabled() != null && req.enabled() ? 1 : 0);
        d.setRemark(req.remark());
        JooqWriters.insert(dsl, JooqTables.FORM_DEFINITION, d);
        refreshCache();
        return d.getId();
    }

    /** 更新表单定义：存在性 + 编码唯一性（编码变更时）+ Schema 校验 + 绑定目标归一，写库后失效缓存。 */
    @Transactional
    public void update(Long id, UpdateFormDefinitionRequest req) {
        FormDefinitionDO existing = JooqWriters.fetchById(dsl, JooqTables.FORM_DEFINITION,
                FormDefinitionDO.class, id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "表单定义不存在");
        }
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
        FormDefinitionDO d = new FormDefinitionDO();
        d.setFormCode(req.formCode());
        d.setFormName(req.formName());
        d.setSchemaJson(req.schemaJson());
        d.setTargetType(target);
        d.setEnabled(req.enabled() != null ? (req.enabled() ? 1 : 0)
                : (existing.getEnabled() == null ? 0 : existing.getEnabled()));
        d.setRemark(req.remark());
        JooqWriters.updateById(dsl, JooqTables.FORM_DEFINITION, id, d);
        refreshCache();
    }

    /** 删除表单定义（逻辑删除），写库后失效缓存。 */
    @Transactional
    public void remove(Long id) {
        FormDefinitionDO existing = JooqWriters.fetchById(dsl, JooqTables.FORM_DEFINITION,
                FormDefinitionDO.class, id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "表单定义不存在");
        }
        JooqWriters.delete(dsl, JooqTables.FORM_DEFINITION, id, true);
        refreshCache();
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private boolean existsByCode(String formCode) {
        return JooqWriters.count(dsl, JooqTables.FORM_DEFINITION,
                DSL.and(JooqWriters.notDeleted(JooqTables.FORM_DEFINITION),
                        JooqTables.FORM_DEFINITION.field("form_code", String.class).eq(formCode))) > 0;
    }

    private FormDefinitionDO loadEnabledByCode(String formCode) {
        Condition cond = DSL.and(JooqWriters.notDeleted(JooqTables.FORM_DEFINITION),
                JooqTables.FORM_DEFINITION.field("form_code", String.class).eq(formCode),
                JooqTables.FORM_DEFINITION.field("enabled", Integer.class).eq(1));
        return JooqWriters.fetchOne(dsl, JooqTables.FORM_DEFINITION, FormDefinitionDO.class, cond);
    }

    private FormDefinitionDO requireByCode(String formCode) {
        FormDefinitionDO d = JooqWriters.fetchOne(dsl, JooqTables.FORM_DEFINITION,
                FormDefinitionDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.FORM_DEFINITION),
                        JooqTables.FORM_DEFINITION.field("form_code", String.class).eq(formCode)));
        if (d == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "表单定义不存在");
        }
        return d;
    }
}
