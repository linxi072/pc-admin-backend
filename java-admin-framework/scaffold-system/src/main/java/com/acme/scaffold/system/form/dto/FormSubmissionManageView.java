package com.acme.scaffold.system.form.dto;

import com.acme.scaffold.system.form.domain.FormSubmissionDO;

import java.time.LocalDateTime;

/**
 * 表单提交管理视图（M47 跨表单统一提交管理）。
 *
 * <p>在渲染面 {@link FormSubmissionView} 之上补充 {@code formName}（表单名称），
 * 使管理后台无需二次联表即可按表单聚合展示；其余字段与 {@code sys_form_submission} 对齐。
 * {@code dataJson} 原样透传，由前端按所属表单 Schema 还原展示。</p>
 *
 * @param id          提交 ID
 * @param formCode    表单编码
 * @param formName    表单名称（来自 sys_form_definition.form_name，缺失时回退为编码）
 * @param submittedBy 提交人
 * @param dataJson    提交值 JSON
 * @param createdAt   提交时间
 */
public record FormSubmissionManageView(
        Long id,
        String formCode,
        String formName,
        String submittedBy,
        String dataJson,
        LocalDateTime createdAt) {

    /** 由持久化对象与表单名称构造视图（null 返回 null）。 */
    public static FormSubmissionManageView of(FormSubmissionDO d, String formName) {
        if (d == null) {
            return null;
        }
        return new FormSubmissionManageView(d.getId(), d.getFormCode(), formName,
                d.getSubmittedBy(), d.getDataJson(), d.getCreatedAt());
    }
}
