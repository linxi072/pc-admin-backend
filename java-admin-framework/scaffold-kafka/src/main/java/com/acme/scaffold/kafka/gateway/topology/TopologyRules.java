package com.acme.scaffold.kafka.gateway.topology;

/**
 * 拓扑规则（纯函数）：acks 合法性 + 分区数推导公式。
 *
 * <p>对应设计稿 §4.2：{@code P = max(目标并行度 × 1.5, 总吞吐 ÷ 单分区消费上限)}。
 * 本例：max(96×1.5, 1.2M÷30K) = max(144, 40) = 144 分区。
 */
public final class TopologyRules {

    private TopologyRules() {
    }

    public static boolean isValidAcks(String acks) {
        return "0".equals(acks) || "1".equals(acks) || "all".equals(acks);
    }

    /**
     * 分区数推导（§4.2 公式）。
     *
     * @param targetThroughput    目标吞吐（msg/s）
     * @param perPartitionCeiling 单分区消费上限（msg/s，轻处理约 3 万，§4.2）
     * @param targetParallelism   目标并行度（消费者数）
     * @return 建议分区数（≥1）
     */
    public static int suggestPartitions(long targetThroughput, int perPartitionCeiling, int targetParallelism) {
        if (targetThroughput < 0) {
            throw new IllegalArgumentException("目标吞吐不能为负: " + targetThroughput);
        }
        if (perPartitionCeiling <= 0) {
            throw new IllegalArgumentException("单分区消费上限必须为正: " + perPartitionCeiling);
        }
        if (targetParallelism <= 0) {
            throw new IllegalArgumentException("目标并行度必须为正: " + targetParallelism);
        }
        long byThroughput = (targetThroughput + perPartitionCeiling - 1) / perPartitionCeiling; // 向上取整
        long byParallelism = Math.round(targetParallelism * 1.5);
        long suggested = Math.max(byThroughput, byParallelism);
        return (int) Math.max(1, Math.min(suggested, Integer.MAX_VALUE));
    }

    /**
     * 单分区流量热点判定（§4.1：单分区流量 > 2× 均值告警）。
     *
     * @param partitionShare 单分区流量占比（0..1）
     * @return true 表示构成热点
     */
    public static boolean isHotPartition(double partitionShare, int partitionCount) {
        if (partitionCount <= 0) {
            throw new IllegalArgumentException("分区数必须为正: " + partitionCount);
        }
        return partitionShare > 2.0 / partitionCount;
    }
}
