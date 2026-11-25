package com.acme.scaffold.cms.article.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 文章排序项：调整单篇文章的排序号。
 *
 * @param id     文章 id（必须存在且未删除）
 * @param sortNo 目标排序号（越小越靠前，0 起步，上限 9999）
 */
public record ArticleSortItem(
        @NotNull Long id,
        @NotNull @Min(0) @Max(9999) Integer sortNo) {
}
