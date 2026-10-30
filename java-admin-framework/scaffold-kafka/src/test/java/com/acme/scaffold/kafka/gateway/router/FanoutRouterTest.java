package com.acme.scaffold.kafka.gateway.router;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FanoutRouter} 扇出路由测试（本地展开 64 分片，不落 Kafka）。
 */
class FanoutRouterTest {

    @Test
    void shardOf_落在合法区间() {
        for (int i = 0; i < 1000; i++) {
            int shard = FanoutRouter.shardOf("uid-" + i);
            assertTrue(shard >= 0 && shard < FanoutRouter.SHARD_COUNT, "分片越界: " + shard);
        }
    }

    @Test
    void shardOf_同key同分片() {
        assertEquals(FanoutRouter.shardOf("uid-42"), FanoutRouter.shardOf("uid-42"));
    }

    @Test
    void expand_非广播仅归属分片() {
        String key = "uid-7";
        List<Integer> targets = FanoutRouter.expand(key, false);
        assertEquals(1, targets.size());
        assertEquals(FanoutRouter.shardOf(key), targets.get(0));
    }

    @Test
    void expand_广播覆盖全部分片() {
        List<Integer> targets = FanoutRouter.expand("uid-x", true);
        assertEquals(FanoutRouter.SHARD_COUNT, targets.size());
        for (int i = 0; i < FanoutRouter.SHARD_COUNT; i++) {
            assertEquals(i, targets.get(i));
        }
    }
}
