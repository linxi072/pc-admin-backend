package com.acme.scaffold.system.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图，绝不外泄 passwordHash / tokenVersion 等敏感字段。
 * <p>约束（多对多）：角色 / 部门均为集合，分别返回 id / 名称 / 编码列表，供前端多选展示。
 * <p>id 与关联 id 列表均为 18 位雪花 ID（{@link com.acme.scaffold.common.IdGenerator}），
 * 超出 JS {@code Number.MAX_SAFE_INTEGER}（2^53），序列化为字符串防前端精度丢失。
 *
 * @param roleIds     角色ID列表
 * @param roleNames   角色名称列表（展示用，与 roleIds 顺序对应）
 * @param roleCodes   角色编码列表（鉴权用，与 roleIds 顺序对应）
 * @param deptIds     部门ID列表
 * @param deptNames   部门名称列表（展示用，与 deptIds 顺序对应）
 */
public record UserView(@JsonSerialize(using = ToStringSerializer.class) Long id,
                       String username, String displayName, String mobile, String email,
                       @JsonSerialize(contentUsing = ToStringSerializer.class) List<Long> roleIds,
                       List<String> roleNames, List<String> roleCodes,
                       @JsonSerialize(contentUsing = ToStringSerializer.class) List<Long> deptIds,
                       List<String> deptNames,
                       String status, LocalDateTime createdAt) {

    public static UserView from(Long id, String username, String displayName, String mobile, String email,
                                List<Long> roleIds, List<String> roleNames, List<String> roleCodes,
                                List<Long> deptIds, List<String> deptNames,
                                String status, LocalDateTime createdAt) {
        return new UserView(id, username, displayName, mobile, email,
                roleIds, roleNames, roleCodes, deptIds, deptNames, status, createdAt);
    }
}
