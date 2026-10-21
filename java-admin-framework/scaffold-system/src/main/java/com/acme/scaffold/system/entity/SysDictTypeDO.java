package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysDictTypeDO {

    private Long id;

    private Long tenantId = 0L;
    private String dictCode;
    private String dictName;
    private String status = "ACTIVE";
    private Integer sortNo = 0;
    private String remark;

    private Integer version = 0;
    private Integer deleted = 0;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
