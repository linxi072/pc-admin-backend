package com.acme.scaffold.system.form;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.form.domain.FormSubmissionDO;
import com.acme.scaffold.system.form.dto.FormDefinitionView;
import com.acme.scaffold.system.form.dto.FormSubmissionView;
import com.acme.scaffold.system.form.rules.FormSubmissionRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 表单提交应用服务（V53 / M22 正式立项）。
 *
 * <p><b>落库目标</b>：动态表单提交值经 schema 校验后写入独立 {@code sys_form_submission} 表，
 * 不侵入业务表、不做运行时 DDL（承接 V47 spike 的 {@code FORM_SUBMISSION} 决策点）。</p>
 *
 * <p><b>提交校验链路</b>：{@link FormDefinitionService#getEnabledByCode} 取启用中定义（停用/不存在抛 NOT_FOUND）
 * → {@link FormSubmissionRules#validateSubmission} 按 schema 字段校验（必填/类型/长度/选项白名单/未知字段拒绝）
 * → 校验通过才 {@link JooqWriters#insert} 落库；任一项校验失败整体拒绝、不落库。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormSubmissionService {

    private final DSLContext dsl;
    private final FormDefinitionService definitionService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 单页上限，防异常大翻页拖垮查询。 */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 提交表单（落库 FORM_SUBMISSION 目标）。
     *
     * @param formCode 表单编码（须为启用中定义）
     * @param values   提交值（字段键 → 值），非空且须通过 schema 校验
     * @param operator 提交人（当前登录用户名）
     * @return 落库后的提交记录视图（含自增 id 与提交时间）
     */
    public FormSubmissionView submit(String formCode, Map<String, Object> values, String operator) {
        FormDefinitionView def = definitionService.getEnabledByCode(formCode);
        List<String> errors = FormSubmissionRules.validateSubmission(def.schemaJson(), values);
        if (!errors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "表单校验未通过：" + String.join("；", errors));
        }
        FormSubmissionDO d = new FormSubmissionDO();
        d.setFormDefinitionId(def.id());
        d.setFormCode(def.formCode());
        d.setSubmittedBy(operator);
        try {
            d.setDataJson(MAPPER.writeValueAsString(values));
        } catch (Exception e) {
            log.error("表单提交数据序列化失败 formCode={}", formCode, e);
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "提交数据序列化失败");
        }
        JooqWriters.insert(dsl, JooqTables.FORM_SUBMISSION, d);
        return FormSubmissionView.of(d);
    }

    /**
     * 表单提交记录分页查询（按提交时间倒序）。
     *
     * @param formCode 表单编码
     * @param page     页码（从 1 起，自动钳制）
     * @param size     每页条数（自动钳制 [1, MAX_PAGE_SIZE]）
     */
    public PageResult<FormSubmissionView> listByForm(String formCode, int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        PageQuery q = PageQuery.of(p, s);
        PageResult<FormSubmissionDO> raw = JooqWriters.page(dsl, JooqTables.FORM_SUBMISSION,
                FormSubmissionDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.FORM_SUBMISSION),
                        JooqTables.FORM_SUBMISSION.field("form_code", String.class).eq(formCode)),
                q,
                JooqTables.FORM_SUBMISSION.field("created_at", LocalDateTime.class).desc());
        return PageResult.of(raw.page(), raw.size(), raw.total(),
                raw.records().stream().map(FormSubmissionView::of).toList());
    }
}
