package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建岗位请求。编码在租户内唯一，创建后仍允许修改（见 UpdatePostRequest）。
 */
public record CreatePostRequest(
        @NotBlank(message = "岗位编码不能为空")
        @Size(max = 64, message = "岗位编码长度不能超过64")
        String postCode,
        @NotBlank(message = "岗位名称不能为空")
        @Size(max = 100, message = "岗位名称长度不能超过100")
        String postName,
        Integer postSort,
        @Size(max = 512, message = "备注长度不能超过512")
        String remark) {
}
