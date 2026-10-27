package com.acme.scaffold.workflow.rules;

/**
 * 流程实例治理纯函数规则（M23 续）：实例挂起/恢复转移合法性判定。
 *
 * <p>与 {@link ClaimRules} 同范式：无副作用静态方法，不触引擎与 DB，返回穷尽枚举由业务层转状态码。
 * 「实例不存在/已结束」由业务层单独以 404/409 拦截，本规则只聚焦于「挂起态」的二次判定。</p>
 */
public final class WorkflowInstanceAdminRules {

    /** 实例挂起/恢复转移判定结果（穷尽）。 */
    public enum InstanceStateTransition {
        OK, ALREADY_SUSPENDED, NOT_SUSPENDED, FINISHED
    }

    private WorkflowInstanceAdminRules() {
    }

    /** 评估挂起：已挂起→ALREADY_SUSPENDED，否则 OK（是否已结束由调用方前置判定）。 */
    public static InstanceStateTransition evaluateSuspend(boolean suspended) {
        return suspended ? InstanceStateTransition.ALREADY_SUSPENDED : InstanceStateTransition.OK;
    }

    /** 评估恢复：未挂起→NOT_SUSPENDED，否则 OK（是否已结束由调用方前置判定）。 */
    public static InstanceStateTransition evaluateResume(boolean suspended) {
        return suspended ? InstanceStateTransition.OK : InstanceStateTransition.NOT_SUSPENDED;
    }
}
