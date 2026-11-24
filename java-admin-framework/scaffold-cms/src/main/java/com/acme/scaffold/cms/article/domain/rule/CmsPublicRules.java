package com.acme.scaffold.cms.article.domain.rule;

/** 公开查询规则（C7，纯函数）：分页上限钳制，防止大页拖垮服务。 */
public final class CmsPublicRules {

    /** 公开端点单页上限（严于管理端，避免公开爬全量）。 */
    public static final int MAX_PAGE_SIZE = 50;

    /** 默认单页大小。 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    private CmsPublicRules() {
    }

    /** 页码钳制：最小 1。 */
    public static int clampPage(int page) {
        return Math.max(page, 1);
    }

    /** 单页大小钳制：[1, MAX_PAGE_SIZE]。 */
    public static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
