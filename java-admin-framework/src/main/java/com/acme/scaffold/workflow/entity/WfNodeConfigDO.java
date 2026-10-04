package com.acme.scaffold.workflow.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WfNodeConfigDO {

    private Long id;

    private Long definitionExtId;
    private String activityId;
    private String nodeName;
    private String approvalMode;
    private BigDecimal approvalRatio;
    private String assigneeType;
    private String assigneeExpression;
    private String rejectPolicy;
    private String rejectTargetActivityId;
    private Integer allowTransfer = 1;
    private Integer allowDelegate = 0;
    private Integer timeoutMinutes;
    private String configJson;
    private LocalDateTime createdAt;
}
