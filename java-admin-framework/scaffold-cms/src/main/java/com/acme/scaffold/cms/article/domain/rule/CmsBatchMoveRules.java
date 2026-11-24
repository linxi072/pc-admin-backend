package com.acme.scaffold.cms.article.domain.rule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 批量移动规则（纯函数）：ids 入参校验、去重归一、数量上限。
 *
 * <p>Service 侧 DB 存在性与目标栏目校验仍需保留；本类只处理「入参是否合法 / 归一后的 ids」，
 * 与 {@link CmsArticleRules} 的分工一致，便于离线单测覆盖边界。</p>
 */
public final class CmsBatchMoveRules {

    /** 单次批量移动上限，防止误操作一次搬空整站。 */
    public static final int MAX_IDS = 200;

    private CmsBatchMoveRules() {
    }

    /**
     * 归一 ids：去 null、去非正数、按首次出现顺序去重。
     *
     * @param ids 原始 ids（可含 null / 重复 / 非法值）
     * @return 归一后的有效 ids（有序、无重复）
     */
    public static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                unique.add(id);
            }
        }
        return new ArrayList<>(unique);
    }

    /** 入参是否合法：归一后非空且不超过上限。 */
    public static boolean validIds(List<Long> ids) {
        List<Long> normalized = normalizeIds(ids);
        return !normalized.isEmpty() && normalized.size() <= MAX_IDS;
    }

    /** 目标栏目是否可作为移动目标（复用栏目 id 合法性口径）。 */
    public static boolean validTargetCategory(Long categoryId) {
        return CmsArticleRules.validCategoryId(categoryId);
    }
}
