package com.acme.scaffold.common.messaging.codec;

import com.acme.scaffold.common.messaging.domain.Envelope;
import com.acme.scaffold.common.messaging.domain.MsgType;

import java.nio.charset.StandardCharsets;

/**
 * 二进制帧编解码（魔数 0x57 = 'W'，版本 0x01）。
 *
 * <p>帧布局（与设计稿 §1.1「二进制协议」对应，小端语义的字段序）：
 * <pre>
 *  +--------+---------+---------+-------+--------------+----------------+---------+---------+----------+---------+---------+----------+
 *  | magic  | version | msgType | flags | varint payLen| zigzag64 seq   | varint  | varint  | zigzag64 | varint  | varint  | payload  |
 *  | 0x57   | 0x01    |         | bit0  |              |                | msgIdLen| keyLen  | timestamp| traceLen|         |          |
 *  +--------+---------+---------+-------+--------------+----------------+---------+---------+----------+---------+---------+----------+
 * </pre>
 * flags bit0=1 时存在 traceId 段（可缺省以省带宽）。
 *
 * <p>容量上限（协议防御，防恶意大帧打爆内存）：payload ≤ 1MB、msgId ≤ 64B、key ≤ 256B、
 * traceId ≤ 128B，与 {@link Envelope} 构造校验保持一致。
 *
 * <p>说明：本编解码面向 {@code byte[]} 契约层，绑定层负责 ByteBuf ↔ byte[] 拷贝；
 * 生产环境可在此接口背后换成零拷贝实现，接口不变。
 */
public final class FrameCodec {

    /** 帧魔数：'W'（WebSocket/Workframe）。 */
    public static final int MAGIC = 0x57;

    /** 协议版本。 */
    public static final int VERSION = 0x01;

    /** flags bit0：携带 traceId。 */
    public static final byte FLAG_HAS_TRACE_ID = 0x01;

    /** payload 上限 1MB（p99 1KB 规格的千倍余量，仍可防滥用）。 */
    public static final int MAX_PAYLOAD = 1 << 20;

    public static final int MAX_MSG_ID = 64;
    public static final int MAX_KEY = 256;
    public static final int MAX_TRACE_ID = 128;

    private FrameCodec() {
    }

    /** 帧编码：Envelope → byte[]。 */
    public static byte[] encode(Envelope envelope) {
        byte[] keyBytes = envelope.keyBytes();
        byte[] msgIdBytes = envelope.msgId().getBytes(StandardCharsets.UTF_8);
        byte[] traceBytes = envelope.traceId().isEmpty()
                ? new byte[0]
                : envelope.traceId().getBytes(StandardCharsets.UTF_8);
        byte[] payload = envelope.payload();

        int size = 4 // 头四字节
                + Varint.varintSize(payload.length)
                + Varint.zigZag64Size(envelope.seq())
                + Varint.varintSize(msgIdBytes.length) + msgIdBytes.length
                + Varint.varintSize(keyBytes.length) + keyBytes.length
                + Varint.zigZag64Size(envelope.timestamp())
                + (envelope.traceId().isEmpty()
                    ? 0
                    : Varint.varintSize(traceBytes.length) + traceBytes.length)
                + payload.length;

        byte[] out = new byte[size];
        int pos = 0;
        out[pos++] = (byte) MAGIC;
        out[pos++] = (byte) VERSION;
        out[pos++] = (byte) envelope.type().code();
        out[pos++] = envelope.traceId().isEmpty() ? 0 : FLAG_HAS_TRACE_ID;
        pos = Varint.writeVarint(out, pos, payload.length);
        pos = Varint.writeZigZag64(out, pos, envelope.seq());
        pos = Varint.writeVarint(out, pos, msgIdBytes.length);
        System.arraycopy(msgIdBytes, 0, out, pos, msgIdBytes.length);
        pos += msgIdBytes.length;
        pos = Varint.writeVarint(out, pos, keyBytes.length);
        System.arraycopy(keyBytes, 0, out, pos, keyBytes.length);
        pos += keyBytes.length;
        pos = Varint.writeZigZag64(out, pos, envelope.timestamp());
        if (!envelope.traceId().isEmpty()) {
            pos = Varint.writeVarint(out, pos, traceBytes.length);
            System.arraycopy(traceBytes, 0, out, pos, traceBytes.length);
            pos += traceBytes.length;
        }
        System.arraycopy(payload, 0, out, pos, payload.length);
        return out;
    }

    /** 帧解码：byte[] → Envelope；协议错误抛 {@link CorruptFrameException}。 */
    public static Envelope decode(byte[] frame) {
        Varint.Reader reader = new Varint.Reader(frame);
        return decode(reader);
    }

    /** 帧解码：从游标读取一帧（调用方负责 expectEnd 或继续读下一帧）。 */
    public static Envelope decode(Varint.Reader reader) {
        int magic = reader.readByte();
        if (magic != MAGIC) {
            throw new CorruptFrameException("帧魔数不匹配: 0x" + Integer.toHexString(magic));
        }
        int version = reader.readByte();
        if (version != VERSION) {
            throw new CorruptFrameException("不支持的帧版本: " + version);
        }
        int typeCode = reader.readByte();
        MsgType type;
        try {
            type = MsgType.fromCode(typeCode);
        } catch (IllegalArgumentException ex) {
            throw new CorruptFrameException("未知消息类型: " + typeCode, ex);
        }
        int flags = reader.readByte();

        long payloadLen = reader.readVarint();
        if (payloadLen < 0 || payloadLen > MAX_PAYLOAD) {
            throw new CorruptFrameException("payload 长度越界: " + payloadLen);
        }
        long seq = reader.readZigZag64();
        if (seq < Envelope.SEQ_UNASSIGNED) {
            throw new CorruptFrameException("seq 为负: " + seq);
        }
        String msgId = reader.readString(MAX_MSG_ID);
        String key = reader.readString(MAX_KEY);
        long timestamp = reader.readZigZag64();
        String traceId = "";
        if ((flags & FLAG_HAS_TRACE_ID) != 0) {
            traceId = reader.readString(MAX_TRACE_ID);
        }
        byte[] payload = reader.readBytes((int) payloadLen);
        reader.expectEnd();
        return new Envelope(seq, msgId, type, key, payload, timestamp, traceId);
    }

    /** 快速判定是否为完整可解码帧（长度前缀已知时用于预检）。 */
    public static boolean looksComplete(byte[] frame) {
        return frame != null && frame.length >= 4 && (frame[0] & 0xFF) == MAGIC;
    }
}
