package com.acme.scaffold.kafka.gateway.topology;

import com.acme.scaffold.common.messaging.domain.Tier;
import com.acme.scaffold.kafka.gateway.admin.TopicAdmin;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Topology} 与 {@link TopicAdmin} 拓扑测试（设计稿 §2.3 / §4.2 / §4.4 / §3.6）。
 *
 * <p>覆盖：designDefault 规格完整性、命名契约、acks/保留/分区数、用户补洞 topic 槽位、
 * validate 通过、TopicAdmin.toNewTopic 纯函数正确性。
 */
class TopologyTest {

    @Test
    void designDefault_规格数量与命名() {
        Topology t = Topology.designDefault(144, 64);
        // rt + durable + 64 fanout + dlq = 67
        assertEquals(67, t.allSpecs().size());
        assertEquals(TopicNames.RAW_RT, t.specOf(Tier.RT).name());
        assertEquals(TopicNames.RAW_DURABLE, t.specOf(Tier.DURABLE).name());
        assertEquals(TopicNames.DLQ, t.specOf(Tier.DLQ).name());
        assertEquals(TopicNames.fanout(0), t.fanoutSpec(0).name());
        assertEquals(TopicNames.fanout(63), t.fanoutSpec(63).name());
    }

    @Test
    void designDefault_rt与durable参数正确() {
        Topology t = Topology.designDefault(144, 64);
        TopicSpec rt = t.specOf(Tier.RT);
        assertEquals(144, rt.partitions());
        assertEquals(3, rt.replicationFactor());
        assertEquals("1", rt.acks());
        assertEquals(6L * 3600_000, rt.retentionMs());

        TopicSpec durable = t.specOf(Tier.DURABLE);
        assertEquals("all", durable.acks());
        assertEquals(72L * 3600_000, durable.retentionMs());
    }

    @Test
    void designDefault_dlq分区与保留() {
        Topology t = Topology.designDefault(144, 64);
        TopicSpec dlq = t.specOf(Tier.DLQ);
        assertEquals(12, dlq.partitions());
        assertEquals(7L * 24 * 3600_000, dlq.retentionMs());
        assertEquals("all", dlq.acks());
    }

    @Test
    void userSeqTopicNames_128槽位() {
        Topology t = Topology.designDefault(144, 64);
        List<String> names = t.userSeqTopicNames();
        assertEquals(TopicNames.USER_SEQ_SLOTS, names.size());
        assertEquals(TopicNames.USER_SEQ_PREFIX + "0", names.get(0));
        assertEquals(TopicNames.USER_SEQ_PREFIX + "127", names.get(127));
    }

    @Test
    void validate_设计默认值通过() {
        assertTrue(Topology.designDefault(144, 64).validate().isEmpty());
    }

    @Test
    void specOf_FANOUT须指定分片() {
        Topology t = Topology.designDefault(144, 64);
        assertThrows(IllegalArgumentException.class, () -> t.specOf(Tier.FANOUT));
    }

    @Test
    void toNewTopic_纯函数正确() {
        Topology t = Topology.designDefault(144, 64);
        TopicSpec spec = t.specOf(Tier.RT);
        NewTopic nt = TopicAdmin.toNewTopic(spec);
        assertEquals(spec.name(), nt.name());
        assertEquals(spec.partitions(), nt.numPartitions());
        assertEquals((short) spec.replicationFactor(), nt.replicationFactor());
        Map<String, String> configs = nt.configs();
        assertEquals(Long.toString(spec.retentionMs()), configs.get("retention.ms"));
        assertEquals(Integer.toString(spec.minInsyncReplicas()), configs.get("min.insync.replicas"));
        assertEquals("delete", configs.get("cleanup.policy"));
    }

    @Test
    void of_同名topic抛异常() {
        TopicSpec a = new TopicSpec("dup", Tier.RT, 16, 3, 2, 1000, "1", 5, "zstd");
        TopicSpec b = new TopicSpec("dup", Tier.DURABLE, 16, 3, 2, 2000, "all", 5, "zstd");
        assertThrows(IllegalArgumentException.class, () -> Topology.of(List.of(a, b)));
    }
}
