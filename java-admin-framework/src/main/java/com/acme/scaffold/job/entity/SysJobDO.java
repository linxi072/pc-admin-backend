package com.acme.scaffold.job.entity;

import com.acme.scaffold.job.domain.JobHandler;
import com.acme.scaffold.job.domain.JobStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务配置实体（对应 {@code sys_job}）。
 * <p>
 * 设计要点：JobRunr 原生的 {@code jobrunr_*} 表由框架内部维护、不可 CRUD，
 * 因此任务定义元数据（配置、参数、统计）独立存放于本表，二者通过
 * {@link #schedulerRef} 关联：CRON/SIMPLE 时存 recurring job id，ONE_TIME 时存一次性 Job 的 UUID。
 */
@Data
public class SysJobDO {

    private Long id;

    private Long tenantId = 0L;

    /** 任务名称（展示用）。 */
    private String jobName;

    /** 任务分组（默认 DEFAULT，用于列表归类）。 */
    private String jobGroup = "DEFAULT";

    /** 处理器标识，对应 {@link JobHandler#key()}。 */
    private String taskKey;

    /** 触发类型：CRON / SIMPLE / ONE_TIME。 */
    private String triggerType;

    /** CRON 表达式（仅 CRON 使用）。 */
    private String cronExpression;

    /** 间隔数值（仅 SIMPLE 使用）。 */
    private Integer intervalValue;

    /** 间隔单位：SECONDS / MINUTES / HOURS / DAYS（仅 SIMPLE 使用）。 */
    private String intervalUnit;

    /** 单次执行时间（仅 ONE_TIME 使用）。 */
    private LocalDateTime oneTimeAt;

    /** 任务参数（JSON 字符串，透传给处理器）。 */
    private String jobParams;

    /** 调度状态：PAUSED / RUNNING / COMPLETED。 */
    private String status = JobStatus.PAUSED.name();

    /** 调度器引用：recurring job id 或一次性 Job 的 UUID 字符串。 */
    private String schedulerRef;

    /** 上次触发时间。 */
    private LocalDateTime lastFireTime;

    /** 下次触发时间（实时计算，持久化仅作参考快照）。 */
    private LocalDateTime nextFireTime;

    /** 累计执行次数。 */
    private Long totalRuns = 0L;

    /** 成功次数。 */
    private Long successRuns = 0L;

    /** 失败次数。 */
    private Long failureRuns = 0L;

    /** 最近一次执行状态：SUCCESS / FAILED。 */
    private String lastStatus;

    /** 最近一次失败信息（截断存储）。 */
    private String lastError;

    /** 最近一次执行耗时（毫秒）。 */
    private Long lastDurationMs;

    private String remark;

    private Integer version = 0;
    private Integer deleted = 0;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
