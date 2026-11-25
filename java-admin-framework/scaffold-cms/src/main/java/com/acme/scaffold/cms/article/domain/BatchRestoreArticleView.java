package com.acme.scaffold.cms.article.domain;

import java.io.Serializable;
import java.util.List;

/**
 * 批量恢复结果：区分"已恢复 / 跳过（不存在或本就未删除）"，便于前端给出可理解的反馈。
 *
 * <p>整体为单事务：要么全部成功，要么整体回滚；skippedIds 仅用于说明哪些 id 未在回收站中命中，
 * 不代表部分成功。</p>
 */
public class BatchRestoreArticleView implements Serializable {

    private int restored;
    private List<Long> skippedIds;

    public BatchRestoreArticleView() {
    }

    public BatchRestoreArticleView(int restored, List<Long> skippedIds) {
        this.restored = restored;
        this.skippedIds = skippedIds;
    }

    public int getRestored() {
        return restored;
    }

    public void setRestored(int restored) {
        this.restored = restored;
    }

    public List<Long> getSkippedIds() {
        return skippedIds;
    }

    public void setSkippedIds(List<Long> skippedIds) {
        this.skippedIds = skippedIds;
    }
}
