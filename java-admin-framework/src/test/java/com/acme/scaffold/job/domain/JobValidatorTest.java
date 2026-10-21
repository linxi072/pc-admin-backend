package com.acme.scaffold.job.domain;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link JobValidator} 单元测试（纯函数，无 Spring / DB）。
 *
 * <p>覆盖：CRON 合法性（复用 JobRunr 解析器）、SIMPLE 间隔下限、ONE_TIME 时间必须晚于当前、
 * params 必须为合法 JSON，以及名称 / 处理器 / 触发类型的必填校验。
 * 这些正是「配置可被调度器接受且不会误触发」的边界，必须逐一守住。
 */
class JobValidatorTest {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

    @Test
    void validCronPasses() {
        assertDoesNotThrow(() -> JobValidator.validate("每5分钟", "demo-echo", JobTriggerType.CRON,
                "*/5 * * * *", null, null, null, null, NOW));
        // 6 段（秒精度）同样合法
        assertDoesNotThrow(() -> JobValidator.validate("每5分钟(秒级)", "demo-echo", JobTriggerType.CRON,
                "0 */5 * * * *", null, null, null, null, NOW));
    }

    @Test
    void invalidCronThrows400() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> JobValidator.validate("坏cron", "demo-echo", JobTriggerType.CRON,
                        "not a cron", null, null, null, null, NOW));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void blankCronThrows() {
        assertThrows(BusinessException.class, () -> JobValidator.validate("空cron", "demo-echo",
                JobTriggerType.CRON, "  ", null, null, null, null, NOW));
    }

    @Test
    void parseCronReturnsExpression() {
        assertNotNull(JobValidator.parseCron("*/5 * * * *"));
    }

    @Test
    void simpleIntervalMustBePositive() {
        assertThrows(BusinessException.class, () -> JobValidator.validate("零间隔", "demo-echo",
                JobTriggerType.SIMPLE, null, 0, JobIntervalUnit.MINUTES, null, null, NOW));
        assertThrows(BusinessException.class, () -> JobValidator.validate("负间隔", "demo-echo",
                JobTriggerType.SIMPLE, null, -1, JobIntervalUnit.MINUTES, null, null, NOW));
    }

    @Test
    void simpleIntervalBelowSmallestThrows() {
        // 3 秒 < JobRunr 最小调度间隔 5 秒
        assertThrows(BusinessException.class, () -> JobValidator.validate("过短", "demo-echo",
                JobTriggerType.SIMPLE, null, 3, JobIntervalUnit.SECONDS, null, null, NOW));
        // 5 秒恰好在边界上，可通过
        assertDoesNotThrow(() -> JobValidator.validate("恰好5秒", "demo-echo",
                JobTriggerType.SIMPLE, null, 5, JobIntervalUnit.SECONDS, null, null, NOW));
    }

    @Test
    void simpleValidIntervalPasses() {
        assertDoesNotThrow(() -> JobValidator.validate("每10分钟", "demo-echo", JobTriggerType.SIMPLE,
                null, 10, JobIntervalUnit.MINUTES, null, "{\"a\":1}", NOW));
    }

    @Test
    void simpleMissingUnitThrows() {
        assertThrows(BusinessException.class, () -> JobValidator.validate("缺单位", "demo-echo",
                JobTriggerType.SIMPLE, null, 5, null, null, null, NOW));
    }

    @Test
    void oneTimeMustBeFuture() {
        Instant past = NOW.minusSeconds(60);
        assertThrows(BusinessException.class, () -> JobValidator.validate("已过", "demo-echo",
                JobTriggerType.ONE_TIME, null, null, null, past, null, NOW));
        // 与当前相等也视为已过（要求「晚于」）
        assertThrows(BusinessException.class, () -> JobValidator.validate("相等", "demo-echo",
                JobTriggerType.ONE_TIME, null, null, null, NOW, null, NOW));
    }

    @Test
    void oneTimeFuturePasses() {
        Instant future = NOW.plusSeconds(3600);
        assertDoesNotThrow(() -> JobValidator.validate("未来", "demo-echo", JobTriggerType.ONE_TIME,
                null, null, null, future, null, NOW));
    }

    @Test
    void oneTimeNullThrows() {
        assertThrows(BusinessException.class, () -> JobValidator.validate("空时间", "demo-echo",
                JobTriggerType.ONE_TIME, null, null, null, null, null, NOW));
    }

    @Test
    void invalidJsonParamsThrows() {
        assertThrows(BusinessException.class, () -> JobValidator.validate("坏参数", "demo-echo",
                JobTriggerType.CRON, "*/5 * * * *", null, null, null, "{not-json}", NOW));
    }

    @Test
    void blankParamsPasses() {
        assertDoesNotThrow(() -> JobValidator.validate("无参数", "demo-echo", JobTriggerType.CRON,
                "*/5 * * * *", null, null, null, null, NOW));
        assertDoesNotThrow(() -> JobValidator.validate("空串参数", "demo-echo", JobTriggerType.CRON,
                "*/5 * * * *", null, null, null, "   ", NOW));
    }

    @Test
    void blankNameThrows() {
        assertThrows(BusinessException.class, () -> JobValidator.validate("  ", "demo-echo",
                JobTriggerType.CRON, "*/5 * * * *", null, null, null, null, NOW));
    }

    @Test
    void blankTaskKeyThrows() {
        assertThrows(BusinessException.class, () -> JobValidator.validate("名称", "  ",
                JobTriggerType.CRON, "*/5 * * * *", null, null, null, null, NOW));
    }

    @Test
    void nullTriggerTypeThrows() {
        assertThrows(BusinessException.class, () -> JobValidator.validate("名称", "demo-echo",
                null, "*/5 * * * *", null, null, null, null, NOW));
    }

    /** 供人工核对：LocalDateTime ↔ Instant 的换算基准（UTC）。 */
    @Test
    void localDateTimeConversionSanity() {
        LocalDateTime ldt = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        assertEquals(Instant.parse("2026-10-11T10:00:00Z"), ldt.toInstant(ZoneOffset.UTC));
    }
}
