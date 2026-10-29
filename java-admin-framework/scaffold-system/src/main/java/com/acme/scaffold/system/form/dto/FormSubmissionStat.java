package com.acme.scaffold.system.form.dto;

import java.util.List;

/**
 * 表单提交全局统计（M47 跨表单统计）。
 *
 * @param totalCount 全部未删除提交总数
 * @param byForm     按表单编码聚合的计数明细（按数量降序）
 */
public record FormSubmissionStat(long totalCount, List<FormSubmissionFormCount> byForm) {
}
