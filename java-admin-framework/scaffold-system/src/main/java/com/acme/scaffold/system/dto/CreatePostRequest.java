package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建岗位请求。编码在租户内唯一，创建后仍允许修改（见 UpdatePostRequest）。
 */
public record CreatePostRequest(
        @NotBlank(message = "{validation.post.code.notBlank}")
        @Size(max = 64, message = "{validation.post.code.size}")
        String postCode,
        @NotBlank(message = "{validation.post.name.notBlank}")
        @Size(max = 100, message = "{validation.post.name.size}")
        String postName,
        Integer postSort,
        @Size(max = 512, message = "{validation.post.remark.size}")
        String remark) {
}
