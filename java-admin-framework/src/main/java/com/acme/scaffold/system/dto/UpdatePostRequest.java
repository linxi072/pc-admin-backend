package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.Size;

/**
 * 编辑岗位请求。所有字段可选，null 表示不修改；
 * postCode 修改时会重新校验租户内唯一性。
 */
public record UpdatePostRequest(
        @Size(max = 64, message = "岗位编码长度不能超过64")
        String postCode,
        @Size(max = 100, message = "岗位名称长度不能超过100")
        String postName,
        Integer postSort,
        String status,
        @Size(max = 512, message = "备注长度不能超过512")
        String remark) {
}
