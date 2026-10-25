package com.acme.scaffold.system.form;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.form.domain.FormDefinitionDO;
import com.acme.scaffold.system.form.dto.FormDefinitionView;
import com.acme.scaffold.system.form.rules.FormSchemaRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表单定义应用服务（V47 低代码表单试点 / M22 spike）。
 *
 * <p><b>spike 收口</b>：只读面（list / getByCode）+ 按编码缓存；管理写面（CRUD）与提交落库
 * 均为正式立项决策点（绑定既有表 vs 独立 form_submission 表），本期不实现。试点表单
 * {@code contact-feedback} 由 V47 迁移播种。</p>
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

    /** 全量失效缓存（后续若增加管理写面，写操作后调用）。 */
    public void refreshCache() {
        codeCache.clear();
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

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
