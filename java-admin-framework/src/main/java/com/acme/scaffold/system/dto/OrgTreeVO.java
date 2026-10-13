package com.acme.scaffold.system.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/**
 * 部门树节点。id / parentId / leaderUserId 为 18 位雪花 ID，超出 JS Number.MAX_SAFE_INTEGER，
 * 序列化为字符串防前端精度丢失。
 */
public record OrgTreeVO(@JsonSerialize(using = ToStringSerializer.class) Long id,
                        @JsonSerialize(using = ToStringSerializer.class) Long parentId,
                        String orgCode, String orgName, String orgType,
                        Integer sortNo, String status,
                        @JsonSerialize(using = ToStringSerializer.class) Long leaderUserId,
                        List<OrgTreeVO> children) {
}
