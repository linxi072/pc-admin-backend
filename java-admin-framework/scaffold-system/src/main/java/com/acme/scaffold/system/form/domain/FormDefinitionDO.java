package com.acme.scaffold.system.form.domain;

import lombok.Data;

/**
 * 表单定义实体（对应 {@code sys_form_definition}，V47 低代码表单试点）。
 *
 * <p>spike 范围：只承载「契约 + 试点表单」的读取；提交落库与绑定目标（BUSINESS_TABLE /
 * FORM_SUBMISSION）为正式立项决策点，本实体不含任何提交态字段。</p>
 */
@Data
public class FormDefinitionDO {

    private Long id;

    /** 租户 ID（默认 0，与全库多租户约定一致）。 */
    private Long tenantId = 0L;

    /** 表单编码（租户内唯一，如 contact-feedback）。 */
    private String formCode;

    /** 表单名称。 */
    private String formName;

    /** 表单 Schema（JSON 字符串：version + fields[]）。 */
    private String schemaJson;

    /** 绑定目标占位：NONE（spike 默认）/ BUSINESS_TABLE / FORM_SUBMISSION。 */
    private String targetType;

    /** 是否启用（1/0）。 */
    private Integer enabled;

    private String remark;

    private Long version;

    private Integer deleted;

    private String createdBy;

    private java.time.LocalDateTime createdAt;

    private String updatedBy;

    private java.time.LocalDateTime updatedAt;
}
