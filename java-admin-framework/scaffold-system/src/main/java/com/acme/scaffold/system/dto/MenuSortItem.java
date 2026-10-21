package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 菜单排序项：拖拽/移动后提交的目标排序值。
 *
 * @param id     菜单 id（必须存在且未删除）
 * @param sortNo 目标排序号（同级内从小到大排列，0 起步）
 */
public record MenuSortItem(
        @NotNull Long id,
        @NotNull @Min(0) Integer sortNo) {
}
