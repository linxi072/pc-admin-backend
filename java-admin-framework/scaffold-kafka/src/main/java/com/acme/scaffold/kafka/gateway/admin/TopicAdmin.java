package com.acme.scaffold.kafka.gateway.admin;

import com.acme.scaffold.kafka.gateway.topology.TopicSpec;
import com.acme.scaffold.kafka.gateway.topology.Topology;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicListing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Topic 管理（对应设计稿 §4.2 / §3.6：幂等建 topic）。
 *
 * <p>{@link #ensureTopics()} 比对集群现有 topic，仅创建缺失项（幂等，可重复执行）。
 * {@link #toNewTopic} 为纯函数，可在无 broker 下单元测试（分区数 / 副本 / 保留 / ISR 一致）。
 */
public final class TopicAdmin {

    private final AdminClient admin;
    private final Topology topology;

    public TopicAdmin(AdminClient admin, Topology topology) {
        this.admin = admin;
        this.topology = topology;
    }

    /** spec → NewTopic（纯函数，含分区/副本/保留/ISR 配置）。 */
    public static NewTopic toNewTopic(TopicSpec spec) {
        NewTopic nt = new NewTopic(spec.name(), spec.partitions(), (short) spec.replicationFactor());
        nt.configs(Map.of(
                "retention.ms", Long.toString(spec.retentionMs()),
                "min.insync.replicas", Integer.toString(spec.minInsyncReplicas()),
                "cleanup.policy", "delete"));
        return nt;
    }

    /** 幂等确保全部 topic 存在（已存在则跳过）。 */
    public void ensureTopics() throws InterruptedException, ExecutionException {
        Set<String> existing = new HashSet<>();
        for (TopicListing listing : admin.listTopics().listings().get()) {
            existing.add(listing.name());
        }
        Collection<NewTopic> toCreate = new ArrayList<>();
        for (TopicSpec spec : topology.allSpecs()) {
            if (!existing.contains(spec.name())) {
                toCreate.add(toNewTopic(spec));
            }
        }
        if (!toCreate.isEmpty()) {
            admin.createTopics(toCreate).all().get();
        }
    }
}
