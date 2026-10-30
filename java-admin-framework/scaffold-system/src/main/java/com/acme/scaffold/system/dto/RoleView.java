package com.acme.scaffold.system.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.Set;

/**
 * 角色视图。
 *
 * <p>id 与 menuIds / apiIds 为 18 位雪花 ID，超出 JS Number.MAX_SAFE_INTEGER，
 * 序列化为字符串防前端精度丢失（角色保存时回显多选）。
 */
public record RoleView(@JsonSerialize(using = ToStringSerializer.class) Long id,
                       String roleCode, String roleName, String roleType, String status,
                       Integer sortNo,
                       @JsonSerialize(contentUsing = ToStringSerializer.class) Set<Long> menuIds,
                       @JsonSerialize(contentUsing = ToStringSerializer.class) Set<Long> apiIds) {

    public static RoleView from(com.acme.scaffold.system.entity.SysRoleDO r) {
        return new RoleView(r.getId(), r.getRoleCode(), r.getRoleName(), r.getRoleType(), r.getStatus(),
                r.getSortNo(), Set.of(), Set.of());
    }
}
