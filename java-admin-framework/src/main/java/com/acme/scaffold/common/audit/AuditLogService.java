package com.acme.scaffold.common.audit;

import com.acme.scaffold.common.api.PageResult;

import java.util.List;

/**
 * 操作审计服务。实现可基于数据库、消息队列或独立审计存储。
 *
 * <p>职责分两部分：
 * <ul>
 *   <li><b>写入</b>：{@link #saveAsync} 由 {@link AuditAspect} 在切面里调用，事务提交后落库；</li>
 *   <li><b>查询</b>：面向管理员的审计追溯能力（设计方案 §20「审计日志可按 traceId 查询且无敏感字段」）。
 *       查询结果一律经 {@link SensitiveMasker} 脱敏后再对外返回。</li>
 * </ul>
 */
public interface AuditLogService {

    /**
     * 异步写入审计记录（事务提交后落库，避免阻塞主流程）。
     */
    void saveAsync(AuditRecord record);

    /**
     * 分页查询审计日志。
     *
     * @param query            查询条件（构造期已完成时间区间等校验）
     * @param sensitiveVisible 是否回传未脱敏摘要，由 Controller 依据 {@code system:audit:sensitive} 权限决定
     */
    PageResult<AuditLogView> query(AuditLogQuery query, boolean sensitiveVisible);

    /**
     * 按 traceId 查询一次请求的完整操作链路（按发生时间正序）。
     *
     * @throws com.acme.scaffold.common.exception.BusinessException traceId 为空时抛 400
     */
    List<AuditLogView> findByTraceId(String traceId, boolean sensitiveVisible);

    /** 按条件统计摘要（总数 / 成功 / 失败 / 慢操作 / 平均与最大耗时）。 */
    AuditLogSummary summary(AuditLogQuery query);
}
