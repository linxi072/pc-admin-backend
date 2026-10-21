package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysMenuDO {

    private Long id;

    private Long tenantId = 0L;
    private Long parentId = 0L;
    private String menuCode;
    private String menuName;
    private String menuType;
    private String routePath;
    private String componentPath;
    private String permissionCode;
    private String icon;
    private Integer visible = 1;
    private Integer keepAlive = 0;
    private String externalUrl;
    private Integer sortNo = 0;
    private String status = "ACTIVE";

    private Integer version = 0;

    private Integer deleted = 0;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
