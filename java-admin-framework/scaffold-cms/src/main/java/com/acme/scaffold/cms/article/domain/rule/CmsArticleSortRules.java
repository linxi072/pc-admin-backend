package com.acme.scaffold.cms.article.domain.rule;

import com.acme.scaffold.cms.article.domain.ArticleSortItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 文章排序规则（C10，纯函数）：排序号范围钳制、排序项归一去重与数量上限。
 *
 * <p>借鉴栏目 {@code sort_no} 与菜单批量排序范式：排序号越小越靠前；DB 存在性校验仍在 Service。
 * 本类只处理「入参是否合法 / 归一后的排序项」，便于离线单测覆盖边界。</p>
 */
public final class CmsArticleSortRules {

    /** 单次批量排序上限，与批量移动/恢复一致。 */
    public static final int MAX_ITEMS = 200;

    /** 排序号下限（最小值，前端 el-input-number 同口径）。 */
    public static final int MIN_SORT_NO = 0;

    /** 排序号上限，防止异常大值挤占正常排序空间（与栏目前端 max 一致）。 */
    public static final int MAX_SORT_NO = 9999;

    private CmsArticleSortRules() {
    }

    /** 排序号归一：null → 0，负数 → 0，超过上限 → 钳到上限。 */
    public static int normalizeSortNo(Integer sortNo) {
        if (sortNo == null) {
            return 0;
        }
        return Math.max(MIN_SORT_NO, Math.min(sortNo, MAX_SORT_NO));
    }

    /** 排序号是否合法（用于请求 DTO 之外的守卫；null 视为不合法）。 */
    public static boolean validSortNo(Integer sortNo) {
        return sortNo != null && sortNo >= MIN_SORT_NO && sortNo <= MAX_SORT_NO;
    }

    /**
     * 归一排序项：过滤 null 项 / null 或非正 id，按 id 首次出现去重，钳制 sortNo。
     *
     * @param items 原始排序项（可含 null / 重复 id / 越界排序号）
     * @return 归一后的排序项（按 id 首次出现顺序，无重复）
     */
    public static List<ArticleSortItem> normalizeItems(List<ArticleSortItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<Long, ArticleSortItem> byId = new LinkedHashMap<>();
        for (ArticleSortItem item : items) {
            if (item == null || item.id() == null || item.id() <= 0) {
                continue;
            }
            byId.putIfAbsent(item.id(), new ArticleSortItem(item.id(), normalizeSortNo(item.sortNo())));
        }
        return new ArrayList<>(byId.values());
    }

    /** 入参是否合法：归一后非空且不超过上限。 */
    public static boolean validItems(List<ArticleSortItem> items) {
        List<ArticleSortItem> normalized = normalizeItems(items);
        return !normalized.isEmpty() && normalized.size() <= MAX_ITEMS;
    }
}
