package com.acme.scaffold.workflow.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 待办/已办任务视图。assigneeUserId 为 18 位雪花 ID，超出 JS Number.MAX_SAFE_INTEGER，
 * 序列化为字符串防前端精度丢失。
 */
public record TaskView(String taskId, String processInstanceId, String activityId, String name,
                       String status,
                       @JsonSerialize(using = ToStringSerializer.class) Long assigneeUserId,
                       LocalDateTime dueAt) {
}
