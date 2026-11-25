package com.acme.scaffold.workflow.instance.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.acme.scaffold.workflow.instance.domain.rule.ClaimRules.Decision;
import org.junit.jupiter.api.Test;

/** 任务认领判定单测：四种互斥结果与 HTTP 状态码映射的口径来源。 */
class ClaimRulesTest {

    @Test
    void nullAssigneeShouldBeClaimable() {
        assertEquals(Decision.CLAIMABLE, ClaimRules.decide(null, 7L));
        assertEquals(Decision.CLAIMABLE, ClaimRules.decide(null, null));
    }

    @Test
    void sameAssigneeShouldBeIdempotent() {
        assertEquals(Decision.ALREADY_MINE, ClaimRules.decide(7L, 7L));
    }

    @Test
    void otherAssigneeShouldBeTakenByOther() {
        assertEquals(Decision.TAKEN_BY_OTHER, ClaimRules.decide(8L, 7L));
        assertEquals(Decision.TAKEN_BY_OTHER, ClaimRules.decide(7L, null));
    }
}
