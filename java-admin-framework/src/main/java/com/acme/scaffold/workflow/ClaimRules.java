package com.acme.scaffold.workflow;

/**
 * 任务认领纯规则（无 Spring / 无 DB 依赖，便于单测）。
 *
 * <p><b>为什么把判定单独抽成静态方法</b>：认领的语义只有三种互斥结果——
 * 任务不存在、已被他人认领、已是自己（幂等）、可认领。
 * 这四点直接决定返回码（404 / 409 / 200 / 200）与是否要调用引擎，
 * 把分支收敛到 {@link #decide} 既能穷尽枚举，又能在无 Flowable / 无数据库的情况下完整单测。
 */
public final class ClaimRules {

    private ClaimRules() {
    }

    /** 认领判定结果：穷尽所有可能，调用方必须处理每一种。 */
    public enum Decision {
        /** 任务在引擎中不存在或已完成。 */
        NOT_FOUND,
        /** 已被其他用户认领，当前用户不可抢占。 */
        TAKEN_BY_OTHER,
        /** 当前用户已认领，再次认领是幂等无操作。 */
        ALREADY_MINE,
        /** 任务尚未指定办理人，当前用户可认领。 */
        CLAIMABLE
    }

    /**
     * 依据任务当前办理人与认领人判定认领结果。
     *
     * <p>调用方需先确认任务存在；本方法不感知「任务是否存在」，
     * 仅基于 assignee 与认领人关系做判定。 null 的 assignee 视为池化（可认领）。
     *
     * @param currentAssignee 任务当前办理人，null 表示尚未认领
     * @param claimerUserId   发起认领的用户
     */
    public static Decision decide(Long currentAssignee, Long claimerUserId) {
        if (currentAssignee == null) {
            return Decision.CLAIMABLE;
        }
        if (currentAssignee.equals(claimerUserId)) {
            return Decision.ALREADY_MINE;
        }
        return Decision.TAKEN_BY_OTHER;
    }
}
