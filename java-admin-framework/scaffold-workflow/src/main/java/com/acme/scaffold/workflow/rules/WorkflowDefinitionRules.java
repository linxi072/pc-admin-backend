package com.acme.scaffold.workflow.rules;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;

/**
 * 流程定义状态机纯函数规则（M23 续）：定义挂起/激活的转移合法性判定。
 *
 * <p>与 {@link ClaimRules}/{@link WorkflowMonitorRules} 同范式：无副作用静态方法，
 * 不触引擎与 DB；返回穷尽枚举，由业务层映射为 HTTP 状态码与异常。</p>
 */
public final class WorkflowDefinitionRules {

    /** 定义状态转移判定结果（穷尽）。 */
    public enum DefinitionStateTransition {
        SUSPEND_OK, ALREADY_SUSPENDED, ACTIVATE_OK, ALREADY_ACTIVE
    }

    private WorkflowDefinitionRules() {
    }

    /**
     * 评估「挂起」操作的合法性。
     *
     * @param currentlySuspended 定义当前是否已挂起
     */
    public static DefinitionStateTransition evaluateSuspend(boolean currentlySuspended) {
        return currentlySuspended ? DefinitionStateTransition.ALREADY_SUSPENDED
                : DefinitionStateTransition.SUSPEND_OK;
    }

    /**
     * 评估「激活」操作的合法性。
     *
     * @param currentlySuspended 定义当前是否已挂起
     */
    public static DefinitionStateTransition evaluateActivate(boolean currentlySuspended) {
        return currentlySuspended ? DefinitionStateTransition.ACTIVATE_OK
                : DefinitionStateTransition.ALREADY_ACTIVE;
    }

    /** 将转移判定映射为业务异常（OK 不抛，交由调用方执行写操作）。 */
    public static void assertTransition(DefinitionStateTransition transition) {
        switch (transition) {
            case ALREADY_SUSPENDED ->
                    throw new BusinessException(CommonErrorCode.CONFLICT, "流程定义已处于挂起状态，无需重复挂起");
            case ALREADY_ACTIVE ->
                    throw new BusinessException(CommonErrorCode.CONFLICT, "流程定义已处于激活状态，无需重复激活");
            case SUSPEND_OK, ACTIVATE_OK -> {
                // 放行，不改变控制流
            }
            default -> throw new IllegalStateException("未覆盖的定义状态转移: " + transition);
        }
    }
}
