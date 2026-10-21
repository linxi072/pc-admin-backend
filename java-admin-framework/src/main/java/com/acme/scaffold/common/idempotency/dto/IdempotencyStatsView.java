package com.acme.scaffold.common.idempotency.dto;

import java.time.LocalDateTime;

/**
 * 幂等记录统计摘要视图（与分页查询相同筛选口径）。
 *
 * <p>面向运维的三个核心问题：<b>现在有多少记录</b>（{@code total}）、
 * <b>有没有卡住</b>（{@code processing} 与 {@code processingShare}——
 * PROCESSING 记录是「客户端拿到响应前断开」的典型残留，正常应在秒级转终态）、
 * <b>清理是否跟上</b>（{@code expiredPending}——已过期待删除条数，
 * 该值持续增长说明 {@code IdempotencyCleanupJob} 未运行或被关闭）。</p>
 *
 * @param total           筛选口径内总记录数
 * @param processing      PROCESSING 状态条数
 * @param success         SUCCESS 状态条数
 * @param failed          FAILED 状态条数
 * @param expiredPending  已过期（expires_at 早于查询时刻）尚未被清理的条数
 * @param processingShare PROCESSING 占比（0~1，保留 4 位小数；total 为 0 时为 0）
 * @param lastCreatedAt   筛选口径内最近一次登记时间（无记录为 null）
 */
public record IdempotencyStatsView(
        long total,
        long processing,
        long success,
        long failed,
        long expiredPending,
        double processingShare,
        LocalDateTime lastCreatedAt) {
}
