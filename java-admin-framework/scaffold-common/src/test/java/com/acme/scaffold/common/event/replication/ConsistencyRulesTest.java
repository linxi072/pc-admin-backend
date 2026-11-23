package com.acme.scaffold.common.event.replication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 跨区域一致性规则单测（E7）：版本单调、向量时钟偏序/合并、并发冲突裁决。 */
class ConsistencyRulesTest {

    @Test
    void shouldApplyOnlyStrictlyNewerVersion() {
        assertTrue(ConsistencyRules.shouldApply(null, 1L));
        assertTrue(ConsistencyRules.shouldApply(1L, 2L));
        assertFalse(ConsistencyRules.shouldApply(2L, 2L));
        assertFalse(ConsistencyRules.shouldApply(3L, 2L));
        assertFalse(ConsistencyRules.shouldApply(1L, null));
    }

    @Test
    void shouldCompareVectorClocks() {
        Map<String, Long> a = Map.of("a", 2L, "b", 1L);
        assertEquals(ConsistencyRules.Relation.EQUAL, ConsistencyRules.compare(a, Map.of("a", 2L, "b", 1L)));
        assertEquals(ConsistencyRules.Relation.BEFORE,
                ConsistencyRules.compare(a, Map.of("a", 1L, "b", 1L))); // remote 落后
        assertEquals(ConsistencyRules.Relation.AFTER,
                ConsistencyRules.compare(a, Map.of("a", 3L, "b", 1L))); // remote 领先
        assertEquals(ConsistencyRules.Relation.CONCURRENT,
                ConsistencyRules.compare(a, Map.of("a", 1L, "b", 5L)));
        assertEquals(ConsistencyRules.Relation.AFTER,
                ConsistencyRules.compare(null, Map.of("a", 1L)));
    }

    @Test
    void shouldMergeVectorClocksTakingMax() {
        Map<String, Long> merged = ConsistencyRules.merge(
                Map.of("a", 3L, "b", 1L), Map.of("a", 1L, "c", 7L));
        assertEquals(3L, merged.get("a"));
        assertEquals(1L, merged.get("b"));
        assertEquals(7L, merged.get("c"));
    }

    @Test
    void shouldTickUnitCount() {
        Map<String, Long> clock = ConsistencyRules.tick(null, "region-a");
        assertEquals(1L, clock.get("region-a"));
        assertEquals(2L, ConsistencyRules.tick(clock, "region-a").get("region-a"));
    }

    @Test
    void shouldResolveConflictDeterministically() {
        ReplicationEnvelope local = new ReplicationEnvelope("e1", "T", "b", java.util.List.of(), 1L, 1, "L");
        ReplicationEnvelope remote = new ReplicationEnvelope("e2", "T", "a", java.util.List.of(), 1L, 2, "R");
        assertNull(ConsistencyRules.resolveConflict(null, null, ConsistencyRules.ConflictPolicy.SOURCE_ID_ASC));
        assertEquals(remote, ConsistencyRules.resolveConflict(null, remote, ConsistencyRules.ConflictPolicy.SOURCE_ID_ASC));
        assertEquals(local, ConsistencyRules.resolveConflict(local, null, ConsistencyRules.ConflictPolicy.SOURCE_ID_ASC));
        assertNull(ConsistencyRules.resolveConflict(local, remote, ConsistencyRules.ConflictPolicy.REJECT));
        assertEquals(remote, ConsistencyRules.resolveConflict(local, remote, ConsistencyRules.ConflictPolicy.SOURCE_ID_ASC));
        assertEquals(local, ConsistencyRules.resolveConflict(local, remote, ConsistencyRules.ConflictPolicy.SOURCE_ID_DESC));
    }
}
