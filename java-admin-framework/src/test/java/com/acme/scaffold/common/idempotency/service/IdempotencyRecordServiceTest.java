package com.acme.scaffold.common.idempotency.service;

import com.acme.scaffold.common.idempotency.dto.IdempotencyStatsView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link IdempotencyRecordService} 离线可测部分：摘要组装（纯函数）。
 *
 * <p>DB 交互路径（分页 / 计数 / 清理删除）按项目约定由 MySQL 集成测试覆盖——
 * mock jOOQ 链式 API 既脆弱又偏离真实实现；故此处仅固化 {@code buildStats} 的
 * 口径：三态计数直出、PROCESSING 占比四舍五入到 4 位、无记录时占比为 0
 * （绝不出现除零 NaN 进入 JSON）、最近登记时间可空直出。</p>
 */
class IdempotencyRecordServiceTest {

    private static final LocalDateTime LAST = LocalDateTime.of(2026, 10, 20, 11, 59, 0);

    @Test
    @DisplayName("ID-S1 混合计数：三态与待清理直出，占比按 4 位小数舍入")
    void buildStats_mixedCounts() {
        IdempotencyStatsView view = IdempotencyRecordService.buildStats(
                100, 3, 90, 7, 12, LAST);
        assertEquals(100L, view.total());
        assertEquals(3L, view.processing());
        assertEquals(90L, view.success());
        assertEquals(7L, view.failed());
        assertEquals(12L, view.expiredPending());
        assertEquals(0.03, view.processingShare());
        assertEquals(LAST, view.lastCreatedAt());
    }

    @Test
    @DisplayName("ID-S2 零记录：占比为 0.0（无除零 NaN），时间可为 null")
    void buildStats_zeroTotal() {
        IdempotencyStatsView view = IdempotencyRecordService.buildStats(0, 0, 0, 0, 0, null);
        assertEquals(0L, view.total());
        assertEquals(0.0, view.processingShare());
        assertNull(view.lastCreatedAt());
    }

    @Test
    @DisplayName("ID-S3 全部处理中：占比 1.0")
    void buildStats_allProcessing() {
        IdempotencyStatsView view = IdempotencyRecordService.buildStats(5, 5, 0, 0, 5, LAST);
        assertEquals(1.0, view.processingShare());
    }

    @Test
    @DisplayName("ID-S4 三分之一处理中：占比 0.3333（第 4 位小数截断后的期望值）")
    void buildStats_roundsToFourDecimals() {
        IdempotencyStatsView view = IdempotencyRecordService.buildStats(3, 1, 2, 0, 0, LAST);
        assertEquals(0.3333, view.processingShare());
    }

    @Test
    @DisplayName("ID-S5 占比分母为负时不出现 NaN（防御脏计数）")
    void buildStats_negativeTotalIsSafe() {
        IdempotencyStatsView view = IdempotencyRecordService.buildStats(-1, 1, 0, 0, 0, LAST);
        assertEquals(0.0, view.processingShare());
    }

    @Test
    @DisplayName("ID-S6 无处理中记录：占比 0.0 且其他计数透传")
    void buildStats_noProcessing() {
        IdempotencyStatsView view = IdempotencyRecordService.buildStats(8, 0, 5, 3, 2, LAST);
        assertEquals(0.0, view.processingShare());
        assertEquals(3L, view.failed());
        assertEquals(2L, view.expiredPending());
    }
}
