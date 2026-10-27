package com.acme.scaffold.system.form.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新表单定义请求（M22 深化：表单定义管理 CRUD）。
 *
 * <p>字段语义同 {@link CreateFormDefinitionRequest}；{@code enabled} 为 null 时保留原值，
 * 其余字段一律以请求值为准覆盖。</p>
 */
public record UpdateFormDefinitionRequest(
        @NotBlank String formCode,
        @NotBlank String formName,
        @NotBlank String schemaJson,
        String targetType,
        Boolean enabled,
        String remark) {
}
