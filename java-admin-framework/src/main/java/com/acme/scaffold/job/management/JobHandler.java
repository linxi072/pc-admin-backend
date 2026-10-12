package com.acme.scaffold.job.management;

/**
 * 任务处理器：业务侧只需实现本接口并标注 {@code @Component}，
 * 即可以 {@link #key()}（taskKey）被「定时任务管理」页面选择并调度，无需感知 JobRunr。
 * <p>
 * 约定：
 * <ul>
 *   <li>{@link #key()} 全局唯一，作为 {@code sys_job.task_key} 的取值；</li>
 *   <li>{@link #execute(JobExecutionContext)} 允许抛异常——抛出即视为本次执行失败，
 *       {@link JobExecutor} 会记录失败状态与错误信息；</li>
 *   <li>处理器内不应吞掉异常后正常返回，否则失败无法被统计。</li>
 * </ul>
 */
public interface JobHandler {

    /** 处理器唯一标识（taskKey）。 */
    String key();

    /** 展示名称（前端下拉选项文案）。 */
    String name();

    /** 执行任务。 */
    void execute(JobExecutionContext context) throws Exception;
}
