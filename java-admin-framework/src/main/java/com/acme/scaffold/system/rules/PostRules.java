package com.acme.scaffold.system.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 岗位管理纯函数规则：编码形态校验、字段规范化、用户岗位集合归一。
 *
 * <p>无副作用、不依赖 Spring 上下文，可直接离线单测；供
 * {@code PostService} 在写入前调用。编码约束从宽（字母/数字/下划线/连字符）——
 * 既避免把「总经理-001」这类常见编码误判为非法，又挡住空白与控制字符注入。
 */
public final class PostRules {

    /** 单个用户最多可兼任的岗位数（与 AssignUserPostsRequest 的 @Size 上限保持一致）。 */
    public static final int MAX_USER_POSTS = 10;

    /** 编码允许字符：字母、数字、下划线、连字符（trim 后整体匹配）。 */
    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]+$");

    private PostRules() {
    }

    /** 编码规范化：去首尾空白；空白串归一为 null。 */
    public static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 编码形态校验（trim 后）：非空且仅含字母/数字/下划线/连字符。 */
    public static boolean isValidCode(String code) {
        String normalized = normalizeCode(code);
        return normalized != null && CODE.matcher(normalized).matches();
    }

    /** 名称规范化：去首尾空白；空白串归一为 null。 */
    public static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 用户岗位集合归一：去 null、去重（保持首次出现顺序）、限制数量上限。
     *
     * @throws IllegalArgumentException 超过 {@link #MAX_USER_POSTS} 个岗位时
     */
    public static List<Long> normalizePostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        Set<Long> deduped = new LinkedHashSet<>();
        for (Long postId : postIds) {
            if (postId != null) {
                deduped.add(postId);
            }
        }
        if (deduped.size() > MAX_USER_POSTS) {
            throw new IllegalArgumentException("单个用户最多可兼任" + MAX_USER_POSTS + "个岗位");
        }
        return new ArrayList<>(deduped);
    }
}
