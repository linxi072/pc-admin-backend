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
    private String configType = "STRING";
    private String remark;
    private String status = "ACTIVE";

    private Integer version = 0;
    private Integer deleted = 0;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
