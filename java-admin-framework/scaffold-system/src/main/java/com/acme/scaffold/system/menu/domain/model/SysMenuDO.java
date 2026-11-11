package com.acme.scaffold.system.menu.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

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

    /** 创建人用户 ID（V83 补列）：种子菜单为 NULL，仅经接口新建的菜单写入当前登录用户。 */
    private Long createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
