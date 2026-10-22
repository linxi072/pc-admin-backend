package com.acme.scaffold.kafka.gateway.settings;

/**
 * 消费组参数矩阵（对应设计稿 §4.3 四消费组：实时 / 持久化 / 扇出 / 审计）。
 *
 * <p>四组独立 groupId、独立 max.poll.records 与隔离级别，互不阻塞（§4.3 三级阶梯的消费侧映射）。
 */
public record ConsumerSettings(String groupId, int maxPollRecords, boolean enableAutoCommit, String isolationLevel) {

    /** 实时消费组：小批量、读未提交（低延迟优先）。 */
    public static ConsumerSettings realtime() {
        return new ConsumerSettings("wg-realtime", 500, false, "read_uncommitted");
    }

    /** 持久化消费组：大批量、读已提交（重放安全）。 */
    public static ConsumerSettings durable() {
        return new ConsumerSettings("wg-durable", 1000, false, "read_committed");
    }

    /** 扇出消费组：本地展开，读未提交。 */
    public static ConsumerSettings fanout() {
        return new ConsumerSettings("wg-fanout", 200, false, "read_uncommitted");
    }

    /** 审计消费组：读已提交（对账口径一致）。 */
    public static ConsumerSettings audit() {
        return new ConsumerSettings("wg-audit", 200, false, "read_committed");
    }
}
