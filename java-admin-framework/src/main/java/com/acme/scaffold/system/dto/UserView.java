package com.acme.scaffold.system.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图，绝不外泄 passwordHash / tokenVersion 等敏感字段。
 * <p>约束（多对多）：角色 / 部门均为集合，分别返回 id / 名称 / 编码列表，供前端多选展示。
 *
 * @param roleIds     角色ID列表
 * @param roleNames   角色名称列表（展示用，与 roleIds 顺序对应）
 * @param roleCodes   角色编码列表（鉴权用，与 roleIds 顺序对应）
 * @param deptIds     部门ID列表
 * @param deptNames   部门名称列表（展示用，与 deptIds 顺序对应）
 */
public record UserView(Long id, String username, String displayName, String mobile, String email,
                      List<Long> roleIds, List<String> roleNames, List<String> roleCodes,
                      List<Long> deptIds, List<String> deptNames,
                      String status, LocalDateTime createdAt) {

    public static UserView from(Long id, String username, String displayName, String mobile, String email,
                                List<Long> roleIds, List<String> roleNames, List<String> roleCodes,
                                List<Long> deptIds, List<String> deptNames,
                                String status, LocalDateTime createdAt) {
        return new UserView(id, username, displayName, mobile, email,
                roleIds, roleNames, roleCodes, deptIds, deptNames, status, createdAt);
    }
}
