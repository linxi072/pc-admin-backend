package com.acme.scaffold.job.port;

/**
 * 任务调用契约：调度器（{@link JobSchedulerPort}）到点触发时回调 {@link #run(Long)}。
 *
 * <p>存在意义：打破 {@code job.port} 与 {@code job.service} 的包级循环——
 * 调度适配器只依赖本契约（与端口同包），由 {@code job.service.JobExecutor} 实现，
 * Spring 按类型注入唯一实现，调度链路行为不变。
 */
public interface JobInvoker {

    /**
     * 执行指定任务：加载任务定义 → 解析处理器 → 执行 → 回写统计与日志。
     *
     * @param jobId 任务主键；为空时实现方应忽略而非抛异常
     */
    void run(Long jobId);
}
