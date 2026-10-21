package com.acme.scaffold.kafka.rules;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OrderingRules} 保序规则行为测试（纯函数，零外部依赖）。
 *
 * <p>覆盖：murmur2 确定性与位级行为、同 key 分区稳定性、分区边界与入参校验、
 * 版本比较（含 null 语义）、shouldProcess/isStale 的严格大于语义。
 */
class OrderingRulesTest {

    // ---- murmur2 确定性 ----

    @Test
    void murmur2_同输入恒等() {
        byte[] key = "order-10086".getBytes();
        assertEquals(OrderingRules.murmur2(key), OrderingRules.murmur2(key.clone()));
    }

    @Test
    void murmur2_不同输入多数不同() {
        // 非严格单射，但业务 key 空间下碰撞应极少：取 200 个顺序 key 统计唯一哈希数
        Set<Integer> hashes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            hashes.add(OrderingRules.murmur2(("order-" + i).getBytes()));
        }
        assertTrue(hashes.size() >= 199, "200 个业务 key 的 murmur2 哈希应几乎无碰撞: " + hashes.size());
    }

    @Test
    void murmur2_空输入有确定值() {
        int empty = OrderingRules.murmur2(new byte[0]);
        assertEquals(empty, OrderingRules.murmur2(new byte[0]));
    }

    @Test
    void murmur2_拒绝null() {
        assertThrows(NullPointerException.class, () -> OrderingRules.murmur2(null));
    }

    // ---- 分区选择 ----

    @Test
    void partitionForKey_同key同分区_不同序列化等价() {
        byte[] k1 = "user-42".getBytes();
        byte[] k2 = "user-42".getBytes();
        assertEquals(
                OrderingRules.partitionForKey(k1, 12),
                OrderingRules.partitionForKey(k2, 12));
        // 字符串重载与字节重载一致（UTF-8）
        assertEquals(
                OrderingRules.partitionForKey(k1, 12),
                OrderingRules.partitionForKey("user-42", 12));
    }

    @Test
    void partitionForKey_结果落在合法区间() {
        for (int p = 1; p <= 64; p++) {
            int partition = OrderingRules.partitionForKey("any-key", p);
            assertTrue(partition >= 0 && partition < p,
                    "分区号应落在 [0," + p + "): " + partition);
        }
    }

    @Test
    void partitionForKey_单分区恒为零() {
        assertEquals(0, OrderingRules.partitionForKey("whatever", 1));
    }

    @Test
    void partitionForKey_null键落入固定分区() {
        int a = OrderingRules.partitionForKey((String) null, 8);
        int b = OrderingRules.partitionForKey((String) null, 8);
        assertEquals(a, b);
        assertTrue(a >= 0 && a < 8);
    }

    @Test
    void partitionForKey_非法分区数抛异常() {
        assertThrows(IllegalArgumentException.class, () -> OrderingRules.partitionForKey("k", 0));
        assertThrows(IllegalArgumentException.class, () -> OrderingRules.partitionForKey("k", -3));
    }

    @Test
    void partitionForKey_批量key分布到多个分区() {
        // 海量 key 场景下不应挤爆单分区：1000 个 key / 16 分区，每个分区都应落有 key
        int partitions = 16;
        Map<Integer, Integer> histogram = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            int p = OrderingRules.partitionForKey("data-" + i, partitions);
            histogram.merge(p, 1, Integer::sum);
        }
        assertTrue(histogram.size() >= partitions / 2,
                "1000 个 key 应较均匀覆盖分区，实际覆盖 " + histogram.size() + "/" + partitions);
    }

    @Test
    void toPositive_负值转非负() {
        assertEquals(0, OrderingRules.toPositive(0));
        assertEquals(1, OrderingRules.toPositive(1));
        assertEquals(Integer.MAX_VALUE, OrderingRules.toPositive(Integer.MAX_VALUE));
        // 与 Kafka Utils.toPositive 位级一致：number & 0x7fffffff
        assertTrue(OrderingRules.toPositive(-1) > 0);
        // 注意 MIN_VALUE 的最高位被掩掉后恰为 0（上游同样如此）
        assertEquals(0, OrderingRules.toPositive(Integer.MIN_VALUE));
        assertTrue(OrderingRules.toPositive(Integer.MIN_VALUE + 1) > 0);
    }

    // ---- 版本比较 ----

    @Test
    void compareVersion_null为最低() {
        assertEquals(-1, OrderingRules.compareVersion(null, 1L));
        assertEquals(1, OrderingRules.compareVersion(1L, null));
        assertEquals(0, OrderingRules.compareVersion(null, null));
        assertEquals(-1, OrderingRules.compareVersion(1L, 2L));
        assertEquals(0, OrderingRules.compareVersion(7L, 7L));
        assertEquals(1, OrderingRules.compareVersion(9L, 2L));
    }

    // ---- shouldProcess / isStale ----

    @Test
    void shouldProcess_首次见到该key即处理() {
        assertTrue(OrderingRules.shouldProcess(null, 1L));
        assertTrue(OrderingRules.shouldProcess(null, 999L));
    }

    @Test
    void shouldProcess_严格大于才处理() {
        assertTrue(OrderingRules.shouldProcess(5L, 6L));
        // 同版本（重复投递）丢弃
        assertNotEquals(true, OrderingRules.shouldProcess(6L, 6L));
        // 旧版本（乱序迟到）丢弃
        assertNotEquals(true, OrderingRules.shouldProcess(6L, 5L));
        assertNotEquals(true, OrderingRules.shouldProcess(6L, 1L));
    }

    @Test
    void shouldProcess_无版本来稿保守放行() {
        assertTrue(OrderingRules.shouldProcess(6L, null));
        assertTrue(OrderingRules.shouldProcess(null, null));
    }

    @Test
    void isStale_与shouldProcess互补() {
        assertTrue(OrderingRules.isStale(6L, 5L));
        assertTrue(OrderingRules.isStale(6L, 6L));
        assertNotEquals(true, OrderingRules.isStale(6L, 7L));
        // 无版本来稿不视为过期
        assertNotEquals(true, OrderingRules.isStale(6L, null));
        // 首次见到不视为过期
        assertNotEquals(true, OrderingRules.isStale(null, 1L));
    }

    @Test
    void shouldProcess_频繁更新序列按版本递进处理() {
        // 模拟同一 key 的连续更新乱序到达：5,3,4,6,2,7 -> 仅 5,6,7 被处理
        Long processed = null;
        int handled = 0;
        long[] arrivals = {5L, 3L, 4L, 6L, 2L, 7L};
        for (long v : arrivals) {
            if (OrderingRules.shouldProcess(processed, v)) {
                processed = v;
                handled++;
            }
        }
        assertEquals(3, handled);
        assertEquals(7L, processed);
    }
}
