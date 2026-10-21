package com.acme.scaffold.kafka.rules;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 海量数据有序消费的保序规则（纯函数，零依赖，便于离线单测）。
 *
 * <p>针对「数据频繁更新 + 需要保证消费时序性」场景，保序由四层机制共同保证：
 * <ol>
 *   <li><b>生产端有序</b>：同 key 消息经 murmur2 哈希稳定落入同一分区，Kafka 分区内 FIFO
 *       有序；生产端开启 {@code enable.idempotence=true}（{@code max.in.flight<=5}）避免重试乱序；</li>
 *   <li><b>分区内串行消费</b>：单线程按分区顺序 poll-处理-提交，禁止同分区并发（见
 *       {@code com.acme.scaffold.kafka.service.OrderedKafkaConsumer}）；</li>
 *   <li><b>乱序兜底</b>：消息携带版本号（version），消费端以「严格大于才处理」丢弃
 *       迟到/重复的旧版本（本类 {@link #shouldProcess}）；</li>
 *   <li><b>键分区选择</b>：{@link #partitionForKey} 与 Kafka 默认分区器行为一致
 *       （murmur2 后取模），用于预演/测试同 key 的分区稳定性。</li>
 * </ol>
 *
 * <p>本类不持有任何状态，全部方法为纯函数；线程安全。
 */
public final class OrderingRules {

    /** Kafka 默认分区器使用的 murmur2 种子（与 org.apache.kafka.common.utils.Utils 一致） */
    private static final int MURMUR_SEED = 0x9747b28c;
    private static final int MURMUR_M = 0x5bd1e995;
    private static final int MURMUR_R = 24;

    private OrderingRules() {
    }

    /**
     * Kafka 兼容的 murmur2 哈希（逐位复刻 {@code org.apache.kafka.common.utils.Utils#murmur2}）。
     *
     * <p>与上游实现保持位级一致，确保本类的分区选择与 Kafka 默认分区器
     * （{@code org.apache.kafka.clients.producer.internals.DefaultPartitioner}）结果相同。
     *
     * @param data 待哈希字节（通常为 message key 的 UTF-8 编码）
     * @return 32 位有符号哈希值（可能为负）
     */
    public static int murmur2(byte[] data) {
        Objects.requireNonNull(data, "data");
        int length = data.length;
        int seed = MURMUR_SEED;
        int m = MURMUR_M;
        int r = MURMUR_R;

        int h = seed ^ length;

        int length4 = length >> 2;
        for (int i = 0; i < length4; i++) {
            int i4 = i << 2;
            int k = (data[i4 + 0] & 0xff)
                    + ((data[i4 + 1] & 0xff) << 8)
                    + ((data[i4 + 2] & 0xff) << 16)
                    + ((data[i4 + 3] & 0xff) << 24);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }

        int lengthMB = length & 0x03;
        if (lengthMB != 0) {
            int index = length4 << 2;
            switch (lengthMB) {
                case 3:
                    h ^= (data[index + 2] & 0xff) << 16;
                    // fallthrough
                case 2:
                    h ^= (data[index + 1] & 0xff) << 8;
                    // fallthrough
                case 1:
                    h ^= data[index] & 0xff;
                    h *= m;
                    break;
                default:
                    break;
            }
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        return h;
    }

    /**
     * 非负化（与 Kafka {@code Utils#toPositive} 一致：{@code number & 0x7fffffff}）。
     *
     * @param number 任意 32 位整数
     * @return 非负整数（0 ~ Integer.MAX_VALUE）
     */
    public static int toPositive(int number) {
        return number & 0x7fffffff;
    }

    /**
     * 按 key 选择目标分区——与 Kafka 默认分区器行为一致。
     *
     * <p>{@code toPositive(murmur2(keyBytes)) % numPartitions}。同一 key 的任意序列化结果
     * 始终得到同一分区（哈希确定、无随机盐），这是「同 key 保序」的根基。
     *
     * @param keyBytes  message key 字节（业务唯一键，如数据 ID 的 UTF-8 编码）
     * @param numPartitions 分区数（必须 &gt; 0）
     * @return 分区号（0 ~ numPartitions-1）
     * @throws IllegalArgumentException numPartitions &lt;= 0 时
     */
    public static int partitionForKey(byte[] keyBytes, int numPartitions) {
        Objects.requireNonNull(keyBytes, "keyBytes");
        if (numPartitions <= 0) {
            throw new IllegalArgumentException("numPartitions 必须大于 0: " + numPartitions);
        }
        return toPositive(murmur2(keyBytes)) % numPartitions;
    }

    /**
     * 按字符串 key 选择目标分区（UTF-8 编码后委托 {@link #partitionForKey(byte[], int)}）。
     *
     * @param key 字符串 key（null 安全：null 视为空键，落入固定分区）
     * @param numPartitions 分区数（必须 &gt; 0）
     * @return 分区号（0 ~ numPartitions-1）
     */
    public static int partitionForKey(String key, int numPartitions) {
        byte[] bytes = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
        return partitionForKey(bytes, numPartitions);
    }

    /**
     * 版本比较：null 视为「未知」（最低）。
     *
     * @param a 版本 A（可为 null）
     * @param b 版本 B（可为 null）
     * @return 负数/零/正数分别表示 a &lt; b / a == b / a &gt; b；两者皆 null 视为相等
     */
    public static int compareVersion(Long a, Long b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    /**
     * 是否应处理来稿：仅当来稿版本<b>严格大于</b>已处理版本时返回 true。
     *
     * <p>语义（at-least-once 投递 + 频繁更新场景）：
     * <ul>
     *   <li>已处理版本为 null（首次见到该 key）：任何带版本的来稿都应处理；</li>
     *   <li>来稿版本 &gt; 已处理版本：数据有更新，处理；</li>
     *   <li>来稿版本 &lt;= 已处理版本：重复投递或乱序迟到的旧版本，丢弃（{@link #isStale}）。</li>
     * </ul>
     *
     * @param processedVersion 该 key 已处理的最大版本（null 表示尚未处理）
     * @param incomingVersion  来稿版本（null 表示无版本信息——保守处理，视为应处理）
     * @return true 表示应处理
     */
    public static boolean shouldProcess(Long processedVersion, Long incomingVersion) {
        if (incomingVersion == null) {
            // 无版本信息的消息无法比对，保守放行（由业务幂等兜底）
            return true;
        }
        return compareVersion(processedVersion, incomingVersion) < 0;
    }

    /**
     * 乱序/过期判定：来稿版本不高于已处理版本（即应被丢弃）。
     *
     * @param processedVersion 已处理版本（null 表示尚未处理）
     * @param incomingVersion  来稿版本
     * @return true 表示来稿为重复或迟到的旧版本
     */
    public static boolean isStale(Long processedVersion, Long incomingVersion) {
        return !shouldProcess(processedVersion, incomingVersion);
    }
}
