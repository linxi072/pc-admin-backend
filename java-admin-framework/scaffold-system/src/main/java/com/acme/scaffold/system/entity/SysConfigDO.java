package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysConfigDO {

    private Long id;

    private Long tenantId = 0L;
    private String configKey;
    private String configName;
    private String configValue;
    private String defaultValue;
    private String configType = "STRING";
    private String remark;
    /** 是否可编辑：1=运行时可改，0=系统内置不可改。 */
    private Integer editable = 1;
    private String status = "ACTIVE";

    private Integer version = 0;
    private Integer deleted = 0;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
