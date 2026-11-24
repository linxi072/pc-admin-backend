package com.acme.scaffold.cms.article.domain;

import java.io.Serializable;
import java.util.List;

/**
 * 批量移动结果：区分"已移动 / 跳过（不存在或已删除）"，便于前端给出可理解的反馈。
 *
 * <p>整体为单事务：要么全部成功，要么整体回滚；skippedIds 仅用于说明哪些 id 未匹配到有效文章，
 * 不代表部分成功。</p>
 */
public class BatchMoveArticleView implements Serializable {

    private int moved;
    private List<Long> skippedIds;
    private Long categoryId;

    public BatchMoveArticleView() {
    }

    public BatchMoveArticleView(int moved, List<Long> skippedIds, Long categoryId) {
        this.moved = moved;
        this.skippedIds = skippedIds;
        this.categoryId = categoryId;
    }

    public int getMoved() {
        return moved;
    }

    public void setMoved(int moved) {
        this.moved = moved;
    }

    public List<Long> getSkippedIds() {
        return skippedIds;
    }

    public void setSkippedIds(List<Long> skippedIds) {
        this.skippedIds = skippedIds;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
