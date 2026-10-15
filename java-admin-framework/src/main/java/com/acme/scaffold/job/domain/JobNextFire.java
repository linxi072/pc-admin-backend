package com.acme.scaffold.job.domain;

import org.jobrunr.scheduling.Schedule;
import org.jobrunr.scheduling.cron.CronExpression;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 「下次执行时间」计算（纯函数，无 Spring / DB 依赖，便于离线单测）。
 *
 * <p>口径：
 * <ul>
 *   <li><b>CRON</b>：复用 JobRunr 的 {@link CronExpression#next(Instant, Instant, ZoneId)}，
 *       与调度器使用同一解析器，展示值与真实触发值一致；</li>
 *   <li><b>SIMPLE</b>：以上次触发时间为基准叠加间隔（从未触发则以当前时间为基准）；</li>
 *   <li><b>ONE_TIME</b>：返回尚未到达的执行时间；已到达（或已执行）返回 {@code null}；</li>
 *   <li>任何无法计算的情形（如 cron 非法、参数缺失）统一返回 {@code null}，由前端展示为「—」，
 *       不抛出异常影响列表渲染。</li>
 * </ul>
 */
public final class JobNextFire {

    private JobNextFire() {
    }

    /**
     * 计算下次执行时间。
     *
     * @param type         触发类型
     * @param cron         cron 表达式（CRON 使用）
     * @param intervalValue 间隔值（SIMPLE 使用）
     * @param intervalUnit  间隔单位（SIMPLE 使用）
     * @param oneTimeAt     单次执行时间（ONE_TIME 使用）
     * @param lastFireTime  上次触发时间（SIMPLE 基准，可为 null）
     * @param now           当前时间（显式传入，便于单测）
     * @param zoneId        时区（cron 计算用；null 取系统默认）
     * @return 下次执行时间；无法计算返回 {@code null}
     */
    public static Instant compute(JobTriggerType type, String cron, Integer intervalValue,
                                  JobIntervalUnit intervalUnit, Instant oneTimeAt,
                                  Instant lastFireTime, Instant now, ZoneId zoneId) {
        if (type == null || now == null) {
            return null;
        }
        ZoneId zone = zoneId == null ? ZoneId.systemDefault() : zoneId;
        return switch (type) {
            case CRON -> nextCron(cron, now, zone);
            case SIMPLE -> nextInterval(intervalValue, intervalUnit, lastFireTime, now);
            case ONE_TIME -> nextOneTime(oneTimeAt, now);
        };
    }

    private static Instant nextCron(String cron, Instant now, ZoneId zone) {
        if (cron == null || cron.isBlank()) {
            return null;
        }
        try {
            return CronExpression.create(cron.trim()).next(now, now, zone);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Instant nextInterval(Integer intervalValue, JobIntervalUnit unit,
                                        Instant lastFireTime, Instant now) {
        if (unit == null || intervalValue == null || intervalValue <= 0) {
            return null;
        }
        Duration interval = unit.toDuration(intervalValue);
        Instant base = lastFireTime != null ? lastFireTime : now;
        return base.plus(interval);
    }

    private static Instant nextOneTime(Instant oneTimeAt, Instant now) {
        if (oneTimeAt == null) {
            return null;
        }
        return oneTimeAt.isAfter(now) ? oneTimeAt : null;
    }

    /** 最小调度间隔（秒），与 {@link Schedule#SMALLEST_SCHEDULE_IN_SECONDS} 对齐，供前端提示。 */
    public static int smallestIntervalSeconds() {
        return Schedule.SMALLEST_SCHEDULE_IN_SECONDS;
    }
}
