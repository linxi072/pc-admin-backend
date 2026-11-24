package com.acme.scaffold.cms.article.domain;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/** 文章批量移动请求：把多篇文章移动到同一目标栏目。 */
public class BatchMoveArticleRequest implements Serializable {

    /** 待移动文章 id 列表（重复 id 由规则层去重，非正数被忽略）。 */
    @NotEmpty
    @Size(max = 200, message = "单次最多移动 200 篇文章")
    private List<Long> ids;

    /** 目标栏目 id（须存在且未删除）。 */
    @NotNull
    private Long categoryId;

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
