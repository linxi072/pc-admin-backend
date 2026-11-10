package com.acme.scaffold.workflow.approval.dto;

/**
 * 审批中心统计：各维度数量快照，供首页卡片展示。
 */
public record ApprovalStats(int todoCount, int doneCount, int instanceCount, int startableCount) {
}
