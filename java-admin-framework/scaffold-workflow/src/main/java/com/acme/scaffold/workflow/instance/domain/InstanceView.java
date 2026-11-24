package com.acme.scaffold.workflow.instance.domain;

import java.time.LocalDateTime;

public record InstanceView(String processInstanceId, String businessType, String businessId, String title,
                          String status, LocalDateTime startedAt, LocalDateTime finishedAt) {
}
