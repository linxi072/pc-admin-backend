package com.acme.scaffold.workflow.model;

/**
 * 自定义工作流节点类型。
 * <ul>
 *   <li>{@code START} 流程发起（唯一）</li>
 *   <li>{@code APPROVAL} 审批节点（支持会签/或签/比例、驳回策略）</li>
 *   <li>{@code SERVICE} 执行步骤（自动动作，由 JavaDelegate 处理）</li>
 *   <li>{@code CC} 抄送节点（通知类步骤）</li>
 *   <li>{@code GATEWAY} 条件分支（排他网关，由出边条件表达式驱动）</li>
 *   <li>{@code END} 流程结束（可多个）</li>
 * </ul>
 */
public enum WorkflowNodeType {
    START,
    APPROVAL,
    SERVICE,
    CC,
    GATEWAY,
    END
}
