package com.acme.scaffold.workflow.admin.domain.rule;

import com.acme.scaffold.workflow.instance.domain.rule.ClaimRules;
import java.util.Locale;

/**
 * 流程实例治理纯函数规则（M23 续）：实例挂起/恢复转移合法性判定 + 治理审计 ID 构造。
 *
 * <p>与 {@link ClaimRules} 同范式：无副作用静态方法，不触引擎与 DB，返回穷尽枚举由业务层转状态码。
 * 「实例不存在/已结束」由业务层单独以 404/409 拦截，本规则只聚焦于「挂起态」的二次判定。</p>
 */
public final class WorkflowInstanceAdminRules {

    /** wf_approval_record.operation_id 列宽（V32 建表：VARCHAR(64)，且带 uk_operation_id 唯一键）。 */
    public static final int OPERATION_ID_MAX_LENGTH = 64;

    /** 实例挂起/恢复转移判定结果（穷尽）。 */
    public enum InstanceStateTransition {
        OK, ALREADY_SUSPENDED, NOT_SUSPENDED, FINISHED
    }

    private WorkflowInstanceAdminRules() {
    }

    /**
     * 构造治理审计的 operationId：保留「action-实例ID」可读前缀，尾部拼接调用方给出的唯一后缀。
     *
     * <p>原实现为 {@code action + "-" + 实例ID + "-" + System.currentTimeMillis()}：
     * {@code reassign} 会在同一毫秒内为多个活跃任务各插一条 REASSIGN 审计，时间戳完全相同，
     * 直接撞 {@code uk_operation_id} 令整个事务回滚。唯一后缀由调用方生成（序号 + 随机段），
     * 本方法只负责保留可读前缀并保证总长不越过列宽；前缀过长时截断，唯一性仍由尾部后缀保证。</p>
     *
     * @param action            审计动作，如 REASSIGN / SUSPEND / MIGRATE
     * @param processInstanceId 流程实例 ID
     * @param uniqueSuffix      调用方生成的唯一后缀（同一事务内多条记录必须互不相同）
     */
    public static String operationId(String action, String processInstanceId, String uniqueSuffix) {
        String a = (action == null || action.isBlank() ? "op" : action).toLowerCase(Locale.ROOT);
        String pi = processInstanceId == null ? "" : processInstanceId;
        String suffix = "-" + (uniqueSuffix == null ? "" : uniqueSuffix);
        // 后缀保留尾部（唯一性所在），给前缀至少留 8 字符可读空间
        int suffixBudget = OPERATION_ID_MAX_LENGTH - 8;
        if (suffix.length() > suffixBudget) {
            suffix = suffix.substring(suffix.length() - suffixBudget);
        }
        int prefixBudget = OPERATION_ID_MAX_LENGTH - suffix.length();
        String prefix = a + "-" + pi;
        if (prefix.length() > prefixBudget) {
            prefix = prefix.substring(0, prefixBudget);
        }
        return prefix + suffix;
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
