package com.acme.scaffold.job.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志实体（对应 {@code sys_job_log}）。
 * <p>每次触发器唤醒并执行处理器后写入一条，供列表页「执行日志」查看与耗时趋势图渲染。
 */
@Data
public class SysJobLogDO {

    private Long id;

    private Long tenantId = 0L;

    /** 关联 {@code sys_job.id}。 */
    private Long jobId;

    /** 任务名称冗余（任务删除后日志仍可读）。 */
    private String jobName;

    /** 处理器标识冗余。 */
    private String taskKey;

    /** 触发类型冗余。 */
    private String triggerType;

    /** 本次触发时间。 */
    private LocalDateTime fireTime;

    /** 执行结束时间。 */
    private LocalDateTime finishedTime;

    /** 执行耗时（毫秒）。 */
    private Long durationMs;

    /** 执行结果：SUCCESS / FAILED。 */
    private String status;

    /** 失败信息（截断存储）。 */
    private String errorMessage;

    /** 链路追踪 ID（来自 MDC，便于与审计/异常日志串联）。 */
    private String traceId;

    private LocalDateTime createdAt;
}
