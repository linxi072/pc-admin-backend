package com.acme.scaffold.cms.article.domain.rule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 回收站规则（纯函数，C9）：批量恢复的 ids 归一、去重与上限校验。
 *
 * <p>与 {@link CmsBatchMoveRules} 保持同一批量口径（去 null/非正数、按首次出现去重、上限 200），
 * 但独立成类便于回收站语义单独演进与离线单测；DB 存在性与 deleted 状态校验仍在 Service。</p>
 */
public final class CmsRecycleRules {

    /** 单次批量恢复上限，与批量移动一致，防止误操作一次性恢复整站回收站。 */
    public static final int MAX_IDS = 200;

    private CmsRecycleRules() {
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
}
