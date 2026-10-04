package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysOrgDO {

    private Long id;

    private Long tenantId = 0L;
    private Long parentId = 0L;
    private String ancestors = "0";
    private String orgCode;
    private String orgName;
    private String orgType = "DEPARTMENT";
    private Integer sortNo = 0;
    private Long leaderUserId;
    private String status = "ACTIVE";

    private Integer version = 0;

    private Integer deleted = 0;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
