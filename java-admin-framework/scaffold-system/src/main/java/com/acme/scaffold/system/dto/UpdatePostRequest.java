package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.Size;

/**
 * 编辑岗位请求。所有字段可选，null 表示不修改；
 * postCode 修改时会重新校验租户内唯一性。
 */
public record UpdatePostRequest(
        @Size(max = 64, message = "{validation.post.code.size}")
        String postCode,
        @Size(max = 100, message = "{validation.post.name.size}")
        String postName,
        Integer postSort,
        String status,
        @Size(max = 512, message = "{validation.post.remark.size}")
        String remark) {
}
