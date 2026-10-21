package com.acme.scaffold.system.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/**
 * 部门树节点。id / parentId / leaderUserId 为 18 位雪花 ID，超出 JS Number.MAX_SAFE_INTEGER，
 * 序列化为字符串防前端精度丢失。
 * <p>leaderUserName 为 leaderUserId 对应的用户姓名（displayName，为空时回落 username），
 * 由 {@code OrgService.tree()} 一次批量查询填充，供前端「负责人」列直接展示，
 * 避免前端逐行按 id 回查用户（N+1）。
 */
public record OrgTreeVO(@JsonSerialize(using = ToStringSerializer.class) Long id,
                        @JsonSerialize(using = ToStringSerializer.class) Long parentId,
                        String orgCode, String orgName, String orgType,
                        Integer sortNo, String status,
                        @JsonSerialize(using = ToStringSerializer.class) Long leaderUserId,
                        String leaderUserName,
                        List<OrgTreeVO> children) {
}
