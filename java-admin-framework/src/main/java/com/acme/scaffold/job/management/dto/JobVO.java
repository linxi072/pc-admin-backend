package com.acme.scaffold.job.management.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 定时任务列表/详情视图。
 *
 * <p>主键为 18 位雪花 ID（{@link com.acme.scaffold.common.IdGenerator}），超出 JavaScript
 * {@code Number.MAX_SAFE_INTEGER}（2^53），若以 JSON number 上链会被前端 round 导致按 ID
 * 的操作（启动/暂停/编辑/删除/日志）全部失配，故 id 统一序列化为字符串。
 *
 * @param id              主键（字符串序列化，防前端精度丢失）
 * @param jobName         任务名称
 * @param jobGroup        任务分组
 * @param taskKey         处理器标识
 * @param taskKeyName     处理器展示名（由注册中心反查；未注册时为原值）
 * @param triggerType     触发类型编码
 * @param triggerTypeLabel 触发类型中文名
 * @param cronExpression  cron 表达式
 * @param intervalValue   间隔值
 * @param intervalUnit    间隔单位编码
 * @param intervalLabel   「值 + 单位」可读文本（如「5 分钟」）
 * @param oneTimeAt       单次执行时间
 * @param jobParams       任务参数（JSON 文本）
 * @param status          调度状态编码
 * @param statusLabel     调度状态中文名
 * @param lastFireTime    上次触发时间
 * @param nextFireTime    下次执行时间（实时计算）
 * @param totalRuns       累计执行次数
 * @param successRuns     成功次数
 * @param failureRuns     失败次数
 * @param lastStatus      最近执行状态（SUCCESS / FAILED）
 * @param lastError       最近失败信息
 * @param lastDurationMs  最近执行耗时（毫秒）
 * @param remark          备注
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 */
public record JobVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String jobName,
        String jobGroup,
        String taskKey,
        String taskKeyName,
        String triggerType,
        String triggerTypeLabel,
        String cronExpression,
        Integer intervalValue,
        String intervalUnit,
        String intervalLabel,
        LocalDateTime oneTimeAt,
        String jobParams,
        String status,
        String statusLabel,
        LocalDateTime lastFireTime,
        LocalDateTime nextFireTime,
        Long totalRuns,
        Long successRuns,
        Long failureRuns,
        String lastStatus,
        String lastError,
        Long lastDurationMs,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
