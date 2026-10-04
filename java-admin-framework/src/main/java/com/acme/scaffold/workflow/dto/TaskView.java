package com.acme.scaffold.workflow.dto;

import java.time.LocalDateTime;

public record TaskView(String taskId, String processInstanceId, String activityId, String name,
                      String status, Long assigneeUserId, LocalDateTime dueAt) {
}
