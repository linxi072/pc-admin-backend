package com.acme.scaffold.system.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/**
 * 菜单树节点。
 *
 * <p>id / parentId 为 18 位雪花 ID（{@link com.acme.scaffold.common.IdGenerator}），
 * 超出 JavaScript {@code Number.MAX_SAFE_INTEGER}（2^53），以 JSON number 上链会被前端
 * round 导致按 id 的操作（编辑/删除/排序）全部失配，故统一序列化为字符串。
 */
public record MenuTreeVO(@JsonSerialize(using = ToStringSerializer.class) Long id,
                         @JsonSerialize(using = ToStringSerializer.class) Long parentId,
                         String menuCode, String menuName, String menuType,
                         String routePath, String componentPath, String permissionCode, String icon,
                         Integer visible, Integer sortNo, String status,
                         List<MenuTreeVO> children) {
}
