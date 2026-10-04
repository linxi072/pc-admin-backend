package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysApiResourceDO {

    private Long id;

    private Long tenantId = 0L;
    private String resourceName;
    private String permissionCode;
    private String httpMethod;
    private String pathPattern;
    private String controllerMethod;
    private String authMode = "REQUIRED";
    private String status = "ACTIVE";
    private String riskLevel = "NORMAL";

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
