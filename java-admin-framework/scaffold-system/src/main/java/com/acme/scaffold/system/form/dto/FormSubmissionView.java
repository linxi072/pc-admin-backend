package com.acme.scaffold.system.form.dto;

import com.acme.scaffold.system.form.domain.FormSubmissionDO;

import java.time.LocalDateTime;

/**
 * 表单提交视图（对应 {@code sys_form_submission}，V53 / M22 正式立项）。
 *
 * <p>{@code dataJson} 原样透传提交值 JSON，由前端按 schema 还原展示；
 * 时间字段沿用 {@code LocalDateTime} 与全局序列化一致。</p>
 *
 * @param id          提交 ID
 * @param formCode    表单编码
 * @param submittedBy 提交人
 * @param dataJson    提交值 JSON
 * @param createdAt   提交时间
 */
public record FormSubmissionView(
        Long id,
        String formCode,
        String submittedBy,
        String dataJson,
        LocalDateTime createdAt) {

    /** 由持久化对象构造视图（null 返回 null）。 */
    public static FormSubmissionView of(FormSubmissionDO d) {
        if (d == null) {
            return null;
        }
        return new FormSubmissionView(d.getId(), d.getFormCode(), d.getSubmittedBy(),
                d.getDataJson(), d.getCreatedAt());
    }
}
