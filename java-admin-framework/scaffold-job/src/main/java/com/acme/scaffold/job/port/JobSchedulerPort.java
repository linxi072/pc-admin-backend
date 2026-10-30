package com.acme.scaffold.job.port;

import java.time.Duration;
import java.time.Instant;

/**
 * 调度端口：隔离调度引擎（JobRunr）实现细节，业务服务只依赖本接口。
 * <p>
 * 三种触发类型对应三种注册方式：
 * <ul>
 *   <li>{@link #scheduleCron} —— 周期性（crontab）；</li>
 *   <li>{@link #scheduleInterval} —— 周期性（固定间隔）；</li>
 *   <li>{@link #scheduleOneTime} —— 一次性。</li>
 * </ul>
 * 每个任务在调度器中以 {@code sysjob-<jobId>} 作为周期任务 id 或一次性 Job 的 UUID 标识，
 * 返回的引用字符串需持久化到 {@code sys_job.scheduler_ref}，用于暂停（取消调度）。
 */
public interface JobSchedulerPort {

    /** 调度引擎是否可用（不可用时业务降级为仅落库、不调度，并给出明确提示）。 */
    boolean available();

    /**
     * 注册 CRON 周期任务。
     *
     * @return 周期任务引用（recurring job id），用于后续取消
     */
    String scheduleCron(Long jobId, String cron);

    /**
     * 注册固定间隔周期任务。
     *
     * @return 周期任务引用，用于后续取消
     */
    String scheduleInterval(Long jobId, Duration interval);

    /**
     * 注册一次性任务。
     *
     * @return 一次性 Job 的 UUID 字符串，用于后续取消
     */
    String scheduleOneTime(Long jobId, Instant fireAt);

    /** 取消周期任务（CRON / SIMPLE）。幂等：引用不存在时静默忽略。 */
    void cancelRecurring(String recurringRef);

    /** 取消一次性任务。幂等：任务已执行或不存在时静默忽略。 */
    void cancelOneTime(String uuidRef);
}
