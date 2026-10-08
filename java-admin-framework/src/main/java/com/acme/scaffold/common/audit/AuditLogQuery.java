package com.acme.scaffold.common.audit;

import com.acme.scaffold.common.api.PageQuery;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 审计日志查询入参。
 *
 * <p>时间区间为「闭区间外的常规语义」：{@code [startTime, endTime]}，两端均可为空表示不限。
 * 区间非法（开始晚于结束）在构造期即拒绝，避免带着错误条件打到数据库后返回空结果误导排查。
 *
 * @param page           页码，从 1 开始（由 {@link PageQuery} 钳制）
 * @param size           每页条数（由 {@link PageQuery} 钳制，上限 100）
 * @param moduleCode     模块编码精确匹配，为空不过滤
 * @param operationType  操作类型精确匹配（CREATE/UPDATE/DELETE/APPROVE...），为空不过滤
 * @param operatorId     操作人 ID 精确匹配
 * @param operatorName   操作人名模糊匹配
 * @param success        true=仅成功 / false=仅失败，为空不过滤
 * @param traceId        链路 ID 精确匹配（设计 §20 要求可按 traceId 查询）
 * @param keyword        操作名 / 请求路径模糊匹配
 * @param minDurationMs  最小耗时，用于筛慢操作，为空不过滤
 * @param startTime      起始时间（含）
 * @param endTime        截止时间（含）
 * @param sortField      排序字段（服务端白名单映射，见 AuditLogServiceImpl）
 * @param sortDirection  排序方向：ASC / DESC
 */
public record AuditLogQuery(int page, int size, String moduleCode, String operationType, Long operatorId,
                            String operatorName, Boolean success, String traceId, String keyword,
                            Long minDurationMs, LocalDateTime startTime, LocalDateTime endTime,
                            String sortField, String sortDirection) {

    public AuditLogQuery {
        // 关键词与编码类入参统一 trim，避免前后空格导致「看起来一样却查不到」
        moduleCode = normalize(moduleCode);
        operationType = normalize(operationType);
        operatorName = normalize(operatorName);
        traceId = normalize(traceId);
        keyword = normalize(keyword);
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "开始时间不能晚于结束时间");
        }
        if (minDurationMs != null && minDurationMs < 0) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "最小耗时不能为负数");
        }
    }

    public PageQuery toPageQuery() {
        return PageQuery.of(page, size, sortField, sortDirection);
    }

    /** 是否存在任意过滤条件——供统计摘要判断是否需要扫全表。 */
    public boolean hasFilter() {
        return StringUtils.hasText(moduleCode) || StringUtils.hasText(operationType) || operatorId != null
                || StringUtils.hasText(operatorName) || success != null || StringUtils.hasText(traceId)
                || StringUtils.hasText(keyword) || minDurationMs != null
                || startTime != null || endTime != null;
    }

    private static String normalize(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
