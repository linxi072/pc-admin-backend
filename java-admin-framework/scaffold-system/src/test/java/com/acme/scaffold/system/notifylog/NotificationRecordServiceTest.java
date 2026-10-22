package com.acme.scaffold.system.notifylog;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link NotificationRecordService} 离线可测部分：摘要 Map 组装（纯函数）。
 *
 * <p>DB 交互路径（分页/计数）按项目约定由 MySQL 集成测试覆盖——mock jOOQ 链式 API
 * 既脆弱又偏离真实实现；故此处仅固化 {@code buildSummary} 的比率与口径，
 * 保证「总记录 = 成功 + 失败 + 跳过」「无记录时失败率为 0」以及 M13 起
 * 「SKIPPED 不计入失败率」等不变量。</p>
 */
class NotificationRecordServiceTest {

    @Test
    void buildSummary_computesFailureRate() {
        Map<String, Object> out = NotificationRecordService.buildSummary(10, 7, 1, null);
        assertEquals(10L, out.get("total"));
        assertEquals(7L, out.get("success"));
        assertEquals(2L, out.get("failure"));
        assertEquals(1L, out.get("skipped"));
        assertEquals(0.2, out.get("failureRate"));
    }

    @Test
    void buildSummary_zeroTotalYieldsZeroRate() {
        Map<String, Object> out = NotificationRecordService.buildSummary(0, 0, 0, null);
        assertEquals(0L, out.get("total"));
        assertEquals(0L, out.get("failure"));
        assertEquals(0.0, out.get("failureRate"));
    }

    @Test
    void buildSummary_allSuccessYieldsZeroRate() {
        Map<String, Object> out = NotificationRecordService.buildSummary(5, 5, 0,
                LocalDateTime.of(2026, 10, 22, 12, 0));
        assertEquals(0.0, out.get("failureRate"));
        assertEquals(LocalDateTime.of(2026, 10, 22, 12, 0), out.get("lastSentAt"));
    }

    @Test
    void buildSummary_skippedNotCountedAsFailure() {
        // 全部退订（3 条 SKIPPED）+ 2 成功：失败数为 0、失败率 0，退订不伪装成故障
        Map<String, Object> out = NotificationRecordService.buildSummary(5, 2, 3, null);
        assertEquals(0L, out.get("failure"));
        assertEquals(0.0, out.get("failureRate"));
        assertEquals(3L, out.get("skipped"));
    }
}
