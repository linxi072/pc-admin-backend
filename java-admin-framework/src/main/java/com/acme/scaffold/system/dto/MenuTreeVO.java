package com.acme.scaffold.system.dto;

import java.util.List;

/**
 * 菜单树节点。
 */
public record MenuTreeVO(Long id, Long parentId, String menuCode, String menuName, String menuType,
                         String routePath, String permissionCode, Integer sortNo, String status,
                         List<MenuTreeVO> children) {
}
