package com.acme.scaffold.job.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建定时任务请求。
 *
 * <p>字段与触发类型的关系：{@code CRON} 用 {@code cronExpression}；{@code SIMPLE} 用
 * {@code intervalValue + intervalUnit}；{@code ONE_TIME} 用 {@code oneTimeAt}。
 * 无关字段可留空，服务端会按触发类型清理。
 *
 * <p>{@code dependsOn} 为前置依赖任务 ID 列表：本任务将等待这些任务均成功执行后，
 * 由依赖编排自动触发（详见 {@code JobDagService}）。空列表表示无依赖。
 *
 * <p>{@code oneTimeAt} 采用 {@code yyyy-MM-dd HH:mm:ss}，与前端日期时间选择器的
 * {@code value-format} 保持一致。
 */
public record JobCreateDTO(
        String jobName,
        String jobGroup,
        String taskKey,
        String triggerType,
        String cronExpression,
        Integer intervalValue,
        String intervalUnit,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime oneTimeAt,
        String jobParams,
        String remark,
        List<Long> dependsOn) {
}
