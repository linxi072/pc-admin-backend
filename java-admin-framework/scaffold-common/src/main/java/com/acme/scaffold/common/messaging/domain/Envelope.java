package com.acme.scaffold.common.messaging.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * 消息信封：系统内唯一流转的消息形态（对应设计稿 §1.1 报文规格与 §4.1 分区键设计）。
 *
 * <p>字段契约：
 * <ul>
 *   <li>{@code seq}：用户维度单调序号，由网关在入站时分配；客户端据此做补洞（§3.6）。</li>
 *   <li>{@code msgId}：全局唯一 ID（UUID），消费端幂等去重键（§4.6）。</li>
 *   <li>{@code key}：分区键 = 业务实体 ID（room_id/device_id/user_id），保证同键 FIFO（§4.1）。</li>
 *   <li>{@code timestamp}：客户端发送时间戳（事件时间），用于还原端到端延迟（§9 Tracing）。</li>
 *   <li>{@code traceId}：全链路透传 ID（客户端生成，§9）。</li>
 * </ul>
 *
 * <p>不可变对象 + 构造期强校验：非法信封不允许进入管线，把异常处理前移到边界。
 */
public final class Envelope {

    /** seq 下限：0 表示「未分配」（上行帧），网关分配后必须为正。 */
    public static final long SEQ_UNASSIGNED = 0L;

    private final long seq;
    private final String msgId;
    private final MsgType type;
    private final String key;
    private final byte[] payload;
    private final long timestamp;
    private final String traceId;

    /**
     * 规范化构造：全部字段强校验，违规直接抛出（调用方按协议错误处理）。
     */
    public Envelope(long seq, String msgId, MsgType type, String key,
                    byte[] payload, long timestamp, String traceId) {
        selfValidate(seq, msgId, type, key, payload, timestamp, traceId);
        this.seq = seq;
        this.msgId = msgId;
        this.type = type;
        this.key = key;
        // 防御性拷贝：调用方持有原数组不得影响信封内容
        this.payload = payload == null ? new byte[0] : payload.clone();
        this.timestamp = timestamp;
        this.traceId = traceId == null ? "" : traceId;
    }

    private static void selfValidate(long seq, String msgId, MsgType type, String key,
                                    byte[] payload, long timestamp, String traceId) {
        if (seq < SEQ_UNASSIGNED) {
            throw new IllegalArgumentException("seq 必须 >= 0（0 表示未分配）: " + seq);
        }
        if (msgId == null || msgId.isBlank()) {
            throw new IllegalArgumentException("msgId 不能为空");
        }
        if (msgId.length() > 64) {
            throw new IllegalArgumentException("msgId 长度超限(64): " + msgId.length());
        }
        if (type == null) {
            throw new IllegalArgumentException("type 不能为空");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("分区键 key 不能为空（§4.1：同键同分区）");
        }
        if (key.length() > 256) {
            throw new IllegalArgumentException("分区键 key 长度超限(256): " + key.length());
        }
        if (timestamp <= 0) {
            throw new IllegalArgumentException("timestamp 必须为正（事件时间）: " + timestamp);
        }
        if (traceId != null && traceId.length() > 128) {
            throw new IllegalArgumentException("traceId 长度超限(128): " + traceId.length());
        }
    }

    /** 便捷工厂：上行帧（seq 未分配，由网关补齐）。 */
    public static Envelope upstream(String msgId, String key, byte[] payload, long timestamp, String traceId) {
        return new Envelope(SEQ_UNASSIGNED, msgId, MsgType.DATA, key, payload, timestamp, traceId);
    }

    /** 便捷工厂：下行帧（seq 已分配）。 */
    public static Envelope downstream(long seq, String msgId, String key, byte[] payload,
                                      long timestamp, String traceId) {
        return new Envelope(seq, msgId, MsgType.DATA, key, payload, timestamp, traceId);
    }

    /** 返回分配了 seq 的副本（网关入站时调用）。 */
    public Envelope withSeq(long newSeq) {
        return new Envelope(newSeq, msgId, type, key, payload, timestamp, traceId);
    }

    public long seq() {
        return seq;
    }

    public String msgId() {
        return msgId;
    }

    public MsgType type() {
        return type;
    }

    public String key() {
        return key;
    }

    /** 防御性拷贝：外部修改不污染信封。 */
    public byte[] payload() {
        return payload.clone();
    }

    public int payloadLength() {
        return payload.length;
    }

    public long timestamp() {
        return timestamp;
    }

    public String traceId() {
        return traceId;
    }

    /** key 的 UTF-8 字节（分区计算 / 序列化共用，避免重复编码）。 */
    public byte[] keyBytes() {
        return key.getBytes(StandardCharsets.UTF_8);
    }

    /** 线格式摘要，用于日志与错误信息（不含 payload 主体，防止日志放大）。 */
    public String digest() {
        return "Envelope{seq=" + seq + ", msgId=" + msgId + ", type=" + type
                + ", key=" + key + ", len=" + payload.length + ", traceId=" + traceId + '}';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Envelope that)) {
            return false;
        }
        return seq == that.seq && timestamp == that.timestamp
                && msgId.equals(that.msgId) && type == that.type && key.equals(that.key)
                && Arrays.equals(payload, that.payload)
                && traceId.equals(that.traceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(seq, msgId, type, key, timestamp, traceId, Arrays.hashCode(payload));
    }

    @Override
    public String toString() {
        return digest();
    }
}
