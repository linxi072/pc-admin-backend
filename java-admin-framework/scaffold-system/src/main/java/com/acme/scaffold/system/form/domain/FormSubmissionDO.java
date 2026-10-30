package com.acme.scaffold.system.form.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表单提交实体（对应 {@code sys_form_submission}，V53 / M22 正式立项）。
 *
 * <p>动态表单的提交值（已按 schema 校验）以 JSON 整体持久化于 {@code dataJson}，
 * 不侵入任何业务表，亦不做运行时 DDL——绑定目标 {@code FORM_SUBMISSION} 的落地形态。</p>
 */
@Data
public class FormSubmissionDO {

    private Long id;

    /** 租户 ID（默认 0，与全库多租户约定一致）。 */
    private Long tenantId = 0L;

    /** 关联表单定义 ID（sys_form_definition.id）。 */
    private Long formDefinitionId;

    /** 表单编码（租户内唯一，如 contact-feedback）。 */
    private String formCode;

    /** 提交人（当前登录用户名；匿名表单可空）。 */
    private String submittedBy;

    /** 提交值 JSON（schema 字段键 → 值，已通过 FormSubmissionRules 校验）。 */
    private String dataJson;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long version;

    private Integer deleted;
}
