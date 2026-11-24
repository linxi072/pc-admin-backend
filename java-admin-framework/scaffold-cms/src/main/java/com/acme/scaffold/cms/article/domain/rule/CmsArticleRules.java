package com.acme.scaffold.cms.article.domain.rule;

import java.util.Locale;
import java.util.Set;

/**
 * 文章业务规则（C1，纯函数）：栏目引用校验、作者回退、标题/关键字规范化、排序字段白名单。
 *
 * <p>Service 侧 DB 存在性校验仍需保留（本类只做「入参是否合法」判定），
 * 但字段归一化与白名单口径统一在此，避免多处重复。</p>
 */
public final class CmsArticleRules {

    /** 与 CmsArticleService 的 ARTICLE_SORT_FIELDS 保持一致（小写归一后比较）。 */
    private static final Set<String> SORT_FIELDS = Set.of(
            "id", "title", "viewcount", "publishedat", "createdat");

    private CmsArticleRules() {
    }

    /** 栏目 ID 是否合法（null/0 视为未选择）。 */
    public static boolean validCategoryId(Long categoryId) {
        return categoryId != null && categoryId > 0;
    }

    /** 作者为空时回退到当前操作人。 */
    public static String resolveAuthor(String author, String currentOperator) {
        return author != null && !author.isBlank() ? author : currentOperator;
    }

    /** 标题规范化：去首尾空白；空标题判为非法。 */
    public static String normalizeTitle(String title) {
        return title == null ? "" : title.trim();
    }

    public static boolean validTitle(String title) {
        String t = normalizeTitle(title);
        return !t.isEmpty() && t.length() <= 255;
    }

    /** 关键字规范化：用于 LIKE 查询的 %keyword% 模式；空返回 null（不加条件）。 */
    public static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim() + "%";
    }

    /** 排序字段是否在白名单内（大小写不敏感）。 */
    public static boolean validSortField(String field) {
        if (field == null || field.isBlank()) {
            return true; // 空表示使用默认排序
        }
        return SORT_FIELDS.contains(field.trim().toLowerCase(Locale.ROOT));
    }

    /** 排序方向规范化：仅 asc/desc 有效，其余回退默认 desc。 */
    public static String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return "desc";
        }
        String d = direction.trim().toLowerCase(Locale.ROOT);
        return "asc".equals(d) || "desc".equals(d) ? d : "desc";
    }
}
