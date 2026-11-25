package com.acme.scaffold.cms.article.domain;

import java.io.Serializable;
import java.util.List;

/**
 * 批量排序结果：区分"已排序 / 跳过（不存在或已删除）"，便于前端反馈。
 *
 * <p>整体为单事务：要么全部成功，要么整体回滚；skippedIds 仅说明哪些 id 未在有效文章中命中。</p>
 */
public class BatchSortArticleView implements Serializable {

    private int sorted;
    private List<Long> skippedIds;

    public BatchSortArticleView() {
    }

    public BatchSortArticleView(int sorted, List<Long> skippedIds) {
        this.sorted = sorted;
        this.skippedIds = skippedIds;
    }

    public int getSorted() {
        return sorted;
    }

    public void setSorted(int sorted) {
        this.sorted = sorted;
    }

    public List<Long> getSkippedIds() {
        return skippedIds;
    }

    public void setSkippedIds(List<Long> skippedIds) {
        this.skippedIds = skippedIds;
    }
}
