package com.acme.scaffold.workbench.report.dto;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;

import java.time.LocalDate;

/**
 * 报表查询入参。
 *
 * <p>构造期即 fail-fast 校验：{@code rangeType} 必填；若 {@code CUSTOM} 须同时提供 start/end 且 start ≤ end。
 * 其余字段均为可选，缺省时取合理默认（type=USAGE、sortBy=COUNT、order=DESC）。</p>
 *
 * @param type     报表类型（默认 USAGE）
 * @param rangeType 时间范围类型（必填）
 * @param start    自定义范围起始日期（仅 CUSTOM 使用）
 * @param end      自定义范围结束日期（仅 CUSTOM 使用）
 * @param module   类别筛选（module_code，可选；为空表示不筛选）
 * @param success  状态筛选（true=成功 / false=失败，可选；为空表示不筛选）
 * @param keyword  关键字筛选（按类型作用于不同字段：JOB_EXEC→任务名、LOGIN→用户名、API_CALL→请求路径；其余类型忽略）
 * @param sortBy   类别统计排序字段（默认 COUNT）
 * @param order    排序方向（默认 DESC）
 */
public record ReportQuery(ReportType type, ReportRange rangeType, LocalDate start, LocalDate end,
                          String module, Boolean success, String keyword, SortField sortBy, SortOrder order) {

    public ReportQuery {
        if (rangeType == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "rangeType 不能为空");
        }
        // 提前解析一次以触发 CUSTOM 起止日期校验，将非法请求挡在 Service 之外
        rangeType.resolve(java.time.LocalDateTime.now(), start, end);
        if (type == null) type = ReportType.USAGE;
        if (sortBy == null) sortBy = SortField.COUNT;
        if (order == null) order = SortOrder.DESC;
    }
}
