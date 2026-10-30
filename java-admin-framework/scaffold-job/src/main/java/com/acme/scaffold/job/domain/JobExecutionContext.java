package com.acme.scaffold.job.domain;

/**
 * 任务执行上下文：传递给 {@link JobHandler#execute(JobExecutionContext)} 的运行期信息。
 *
 * @param jobId     任务主键（{@code sys_job.id}）
 * @param jobName   任务名称
 * @param taskKey   处理器标识
 * @param paramsRaw 原始参数（JSON 字符串，可能为空）
 */
public record JobExecutionContext(Long jobId, String jobName, String taskKey, String paramsRaw) {
}
