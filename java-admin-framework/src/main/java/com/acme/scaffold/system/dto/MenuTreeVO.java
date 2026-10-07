package com.acme.scaffold.system.dto;

import java.util.List;

/**
 * 菜单树节点。
 */
public record MenuTreeVO(Long id, Long parentId, String menuCode, String menuName, String menuType,
                         String routePath, String componentPath, String permissionCode, String icon,
                         Integer visible, Integer sortNo, String status,
                         List<MenuTreeVO> children) {
}
