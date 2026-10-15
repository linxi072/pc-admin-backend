package com.acme.scaffold.job.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link JobNextFire} 单元测试（纯函数，无 Spring / DB）。
 *
 * <p>用固定 {@code now} 与 UTC 时区构造确定性用例，覆盖三种触发类型的下一执行时间口径，
 * 以及非法 / 缺失配置一律返回 {@code null}（列表不因坏数据而报错）。
 */
class JobNextFireTest {

    /** 2026-10-11 10:30:00 UTC（周六）。 */
    private static final Instant NOW = Instant.parse("2026-10-11T10:30:00Z");

    // ---------------- CRON ----------------

    @Test
    void cronEvery5Minutes() {
        Instant next = JobNextFire.compute(JobTriggerType.CRON, "*/5 * * * *", null, null,
                null, null, NOW, ZoneOffset.UTC);
        assertEquals(Instant.parse("2026-10-11T10:35:00Z"), next);
    }

    @Test
    void cronDailyMidnightRollsToNextDay() {
        // 5 段 "0 0 * * *" = 每天 00:00；当前 10:30 → 次日 00:00
        Instant next = JobNextFire.compute(JobTriggerType.CRON, "0 0 * * *", null, null,
                null, null, NOW, ZoneOffset.UTC);
        assertEquals(Instant.parse("2026-10-12T00:00:00Z"), next);
    }

    @Test
    void cronWithSecondsField() {
        // 6 段 "0 */5 * * * *" = 秒=0、每 5 分钟
        Instant next = JobNextFire.compute(JobTriggerType.CRON, "0 */5 * * * *", null, null,
                null, null, NOW, ZoneOffset.UTC);
        assertEquals(Instant.parse("2026-10-11T10:35:00Z"), next);
    }

    @Test
    void cronInvalidReturnsNull() {
        assertNull(JobNextFire.compute(JobTriggerType.CRON, "not a cron", null, null,
                null, null, NOW, ZoneOffset.UTC));
        assertNull(JobNextFire.compute(JobTriggerType.CRON, null, null, null,
                null, null, NOW, ZoneOffset.UTC));
    }

    // ---------------- SIMPLE ----------------

    @Test
    void simpleFromLastFireAddsInterval() {
        Instant lastFire = Instant.parse("2026-10-11T10:00:00Z");
        Instant next = JobNextFire.compute(JobTriggerType.SIMPLE, null, 5, JobIntervalUnit.MINUTES,
                null, lastFire, NOW, ZoneOffset.UTC);
        assertEquals(Instant.parse("2026-10-11T10:05:00Z"), next);
    }

    @Test
    void simpleWithoutLastFireUsesNow() {
        Instant next = JobNextFire.compute(JobTriggerType.SIMPLE, null, 30, JobIntervalUnit.SECONDS,
                null, null, NOW, ZoneOffset.UTC);
        assertEquals(Instant.parse("2026-10-11T10:30:30Z"), next);
    }

    @Test
    void simpleWithMissingConfigReturnsNull() {
        assertNull(JobNextFire.compute(JobTriggerType.SIMPLE, null, null, JobIntervalUnit.MINUTES,
                null, null, NOW, ZoneOffset.UTC));
        assertNull(JobNextFire.compute(JobTriggerType.SIMPLE, null, 5, null,
                null, null, NOW, ZoneOffset.UTC));
        assertNull(JobNextFire.compute(JobTriggerType.SIMPLE, null, 0, JobIntervalUnit.MINUTES,
                null, null, NOW, ZoneOffset.UTC));
    }

    // ---------------- ONE_TIME ----------------

    @Test
    void oneTimeFutureReturnsSelf() {
        Instant fireAt = Instant.parse("2026-10-11T12:00:00Z");
        assertEquals(fireAt, JobNextFire.compute(JobTriggerType.ONE_TIME, null, null, null,
                fireAt, null, NOW, ZoneOffset.UTC));
    }

    @Test
    void oneTimePastOrNullReturnsNull() {
        Instant past = Instant.parse("2026-10-11T09:00:00Z");
        assertNull(JobNextFire.compute(JobTriggerType.ONE_TIME, null, null, null,
                past, null, NOW, ZoneOffset.UTC));
        assertNull(JobNextFire.compute(JobTriggerType.ONE_TIME, null, null, null,
                null, null, NOW, ZoneOffset.UTC));
    }

    // ---------------- 边界 ----------------

    @Test
    void nullTypeOrNowReturnsNull() {
        assertNull(JobNextFire.compute(null, "*/5 * * * *", null, null,
                null, null, NOW, ZoneOffset.UTC));
        assertNull(JobNextFire.compute(JobTriggerType.CRON, "*/5 * * * *", null, null,
                null, null, null, ZoneOffset.UTC));
    }

    @Test
    void nullZoneFallsBackToSystemDefaultNoThrow() {
        // 仅验证不抛异常（时区为 null 时回退系统默认）
        JobNextFire.compute(JobTriggerType.CRON, "*/5 * * * *", null, null,
                null, null, NOW, null);
    }

    @Test
    void smallestIntervalSecondsMatchesJobRunr() {
        assertEquals(5, JobNextFire.smallestIntervalSeconds());
    }
}
