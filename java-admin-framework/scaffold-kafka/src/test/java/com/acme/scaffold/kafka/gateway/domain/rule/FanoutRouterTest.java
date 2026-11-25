package com.acme.scaffold.kafka.gateway.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.kafka.support.OrderingRules;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 扇出路由单测：64 分片广播与单分片归属，复用统一分区哈希。 */
class FanoutRouterTest {

    @Test
    void shardCountShouldMatchDesign() {
        assertEquals(64, FanoutRouter.SHARD_COUNT);
    }

    @Test
    void shardOfShouldReuseUnifiedPartitionFunction() {
        for (String key : List.of("user-1", "user-2", "order-9", "")) {
            assertEquals(OrderingRules.partitionForKey(key, 64), FanoutRouter.shardOf(key));
            assertTrue(FanoutRouter.shardOf(key) >= 0 && FanoutRouter.shardOf(key) < 64);
        }
    }

    @Test
    void broadcastShouldExpandAllShardsInOrder() {
        List<Integer> all = FanoutRouter.expand("user-1", true);
        assertEquals(64, all.size());
        for (int i = 0; i < 64; i++) {
            assertEquals(i, all.get(i));
        }
    }

    @Test
    void unicastShouldExpandOnlyOwningShard() {
        assertEquals(List.of(FanoutRouter.shardOf("user-1")), FanoutRouter.expand("user-1", false));
    }

    @Test
    void nullKeyShouldBehaveAsEmptyKey() {
        assertEquals(FanoutRouter.shardOf(""), FanoutRouter.shardOf(null));
    }
}
