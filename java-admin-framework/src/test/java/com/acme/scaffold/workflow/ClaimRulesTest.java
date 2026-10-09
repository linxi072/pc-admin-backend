package com.acme.scaffold.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ClaimRules} 单元测试：认领是否安全全靠四种判定的穷尽与正确——
 * 任务不存在由调用方先判，这里只覆盖「基于 assignee 关系的三种结果」与边界。
 */
class ClaimRulesTest {

    // ---------------- 可认领（池化任务，无办理人） ----------------

    @Test
    void nullAssigneeIsClaimable() {
        assertEquals(ClaimRules.Decision.CLAIMABLE, ClaimRules.decide(null, 1001L));
    }

    // ---------------- 已被自己认领（幂等） ----------------

    @Test
    void assigneeEqualsClaimerIsAlreadyMine() {
        assertEquals(ClaimRules.Decision.ALREADY_MINE, ClaimRules.decide(1001L, 1001L));
    }

    // ---------------- 已被他人认领（不可抢占） ----------------

    @Test
    void assigneeDiffersFromClaimerIsTakenByOther() {
        assertEquals(ClaimRules.Decision.TAKEN_BY_OTHER, ClaimRules.decide(2002L, 1001L));
    }

    // ---------------- 边界：claimId 与 assignee 的类型/取值 ----------------

    @Test
    void differentValuesWithSameStringRepresentionStillDiffer() {
        // 强调是基于数值比较而非字符串，避免「007」与「7」被误判为同一人
        assertEquals(ClaimRules.Decision.TAKEN_BY_OTHER, ClaimRules.decide(7L, 7007L));
    }

    @Test
    void zeroUserIdHandledLikeAnyOther() {
        assertEquals(ClaimRules.Decision.ALREADY_MINE, ClaimRules.decide(0L, 0L));
        assertEquals(ClaimRules.Decision.CLAIMABLE, ClaimRules.decide(null, 0L));
    }
}
