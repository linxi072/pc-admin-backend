package com.acme.scaffold.cms.support;

/**
 * 内容发布态。以 VARCHAR 持久化，应用层负责校验与流转。
 *
 * <ul>
 *   <li>{@code DRAFT}：草稿，未对外可见；</li>
 *   <li>{@code PUBLISHED}：已发布，对外可见；</li>
 *   <li>{@code OFFLINE}：已下线，停止对外可见（保留历史）。</li>
 * </ul>
 */
public enum CmsStatus {
    DRAFT,
    PUBLISHED,
    OFFLINE;

    /** 栏目自身的启用态（ACTIVE / DISABLED），与文章发布态共用枚举语义、独立字段。 */
    public static final String CATEGORY_ACTIVE = "ACTIVE";
    public static final String CATEGORY_DISABLED = "DISABLED";

    public static boolean isPublishable(String status) {
        return PUBLISHED.name().equals(status);
    }
}
