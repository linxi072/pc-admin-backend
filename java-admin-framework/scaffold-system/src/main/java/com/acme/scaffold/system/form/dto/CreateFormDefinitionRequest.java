package com.acme.scaffold.system.form.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建表单定义请求（M22 深化：表单定义管理 CRUD）。
 *
 * <p>{@code schemaJson} 为 version + fields[] 的 JSON 字符串，落库前由
 * {@link com.acme.scaffold.system.form.rules.FormSchemaRules#validate} 校验结构合法性；
 * {@code targetType} 白名单见 {@code FormSchemaRules.TARGET_TYPES}，空/null 按 NONE 兜底。</p>
 */
public record CreateFormDefinitionRequest(
        @NotBlank String formCode,
        @NotBlank String formName,
        @NotBlank String schemaJson,
        String targetType,
        Boolean enabled,
        String remark) {
}
