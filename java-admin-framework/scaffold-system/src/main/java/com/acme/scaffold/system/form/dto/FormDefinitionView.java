package com.acme.scaffold.system.form.dto;

import com.acme.scaffold.system.form.domain.FormDefinitionDO;

/**
 * 表单定义视图（对应 {@code sys_form_definition}）。
 *
 * <p>{@code schemaJson} 原样透传 JSON 字符串，由前端 {@code DynamicForm.vue} 解析渲染；
 * {@code enabled} 由整型 0/1 映射为布尔。</p>
 *
 * @param id          定义 ID
 * @param formCode    表单编码（租户内唯一）
 * @param formName    表单名称
 * @param schemaJson  表单 Schema（version + fields[]）
 * @param targetType  绑定目标占位（NONE/BUSINESS_TABLE/FORM_SUBMISSION）
 * @param enabled     是否启用
 * @param remark      备注
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 */
public record FormDefinitionView(
        Long id,
        String formCode,
        String formName,
        String schemaJson,
        String targetType,
        boolean enabled,
        String remark,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt) {

    /** 由持久化对象构造视图（null 返回 null）。 */
    public static FormDefinitionView of(FormDefinitionDO d) {
        if (d == null) {
            return null;
        }
        return new FormDefinitionView(
                d.getId(), d.getFormCode(), d.getFormName(), d.getSchemaJson(),
                d.getTargetType(), d.getEnabled() != null && d.getEnabled() == 1,
                d.getRemark(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
