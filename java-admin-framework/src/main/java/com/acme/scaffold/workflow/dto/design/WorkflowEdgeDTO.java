package com.acme.scaffold.workflow.dto.design;

/**
 * 工作流连线（边）定义。
 * <ul>
 *   <li>普通节点出边：source→target 直接流转。</li>
 *   <li>条件分支(GATEWAY)出边：当 source 为 GATEWAY 且存在多条出边时，
 *       conditionExpression 非空表示该分支的走向条件（EL 表达式，如 {@code amount <= 1000}）；</li>
 *   <li>isDefault：排他网关的默认分支（无符合条件时走此边），一个 GATEWAY 至多一条。</li>
 * </ul>
 */
public record WorkflowEdgeDTO(
        String id,
        String sourceNodeId,
        String targetNodeId,
        String conditionExpression,
        Boolean isDefault) {
}
