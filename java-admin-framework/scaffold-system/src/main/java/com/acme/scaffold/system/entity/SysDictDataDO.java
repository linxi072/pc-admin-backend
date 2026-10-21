package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysDictDataDO {

    private Long id;

    private Long tenantId = 0L;
    private String dictTypeCode;
    private String dictLabel;
    private String dictValue;
    private Integer dictSort = 0;
    private String status = "ACTIVE";
    private String remark;

    private Integer version = 0;
    private Integer deleted = 0;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
