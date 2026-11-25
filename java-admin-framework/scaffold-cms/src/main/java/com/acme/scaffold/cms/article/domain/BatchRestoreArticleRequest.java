package com.acme.scaffold.cms.article.domain;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/** 文章批量恢复请求：把回收站中的多篇文章恢复到正常列表。 */
public class BatchRestoreArticleRequest implements Serializable {

    /** 待恢复文章 id 列表（重复 id 由规则层去重，非正数被忽略）。 */
    @NotEmpty
    @Size(max = 200, message = "单次最多恢复 200 篇文章")
    private List<Long> ids;

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }
}
