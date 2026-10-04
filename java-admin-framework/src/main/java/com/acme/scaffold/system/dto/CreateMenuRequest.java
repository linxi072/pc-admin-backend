package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateMenuRequest(
        Long parentId,
        @NotBlank String menuCode,
        @NotBlank String menuName,
        @NotBlank String menuType,
        String routePath,
        String componentPath,
        String permissionCode,
        String icon,
        Integer visible,
        Integer sortNo,
        String status) {
}
