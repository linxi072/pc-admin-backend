package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 用户岗位分配请求（整体替换语义：提交的列表即该用户的最终岗位集合）。
 *
 * @param postIds 岗位 ID 集合；空列表表示清空该用户全部岗位
 */
public record AssignUserPostsRequest(
        @Size(max = 10, message = "单个用户最多可兼任10个岗位")
        List<Long> postIds) {
}
