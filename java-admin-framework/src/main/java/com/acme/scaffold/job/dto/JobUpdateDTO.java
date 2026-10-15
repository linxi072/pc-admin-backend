package com.acme.scaffold.job.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 更新定时任务请求。字段语义与 {@link JobCreateDTO} 一致。
 *
 * <p>若任务处于运行中，服务端会先取消原调度、写入新配置、再按新配置重启，保证「即时生效」。
 */
public record JobUpdateDTO(
        String jobName,
        String jobGroup,
        String taskKey,
        String triggerType,
        String cronExpression,
        Integer intervalValue,
        String intervalUnit,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime oneTimeAt,
        String jobParams,
        String remark) {
}
