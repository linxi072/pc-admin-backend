package com.acme.scaffold.system.form.domain.model;

import lombok.Data;

/** 表单定义版本快照（E8）：发布时固化的不可变 schema，支持回滚。 */
@Data
public class FormDefinitionVersionDO {

    private Long id;
    private Long tenantId = 0L;
    private Long formDefinitionId;
    private String formCode;
    private Integer versionNo;
    private String formName;
    private String schemaJson;
    private String targetType;
    private Integer enabled;
    private String remark;
    private Integer published;
    private Long version;
    private Integer deleted;
    private String createdBy;
    private java.time.LocalDateTime createdAt;
    private String updatedBy;
    private java.time.LocalDateTime updatedAt;
}
