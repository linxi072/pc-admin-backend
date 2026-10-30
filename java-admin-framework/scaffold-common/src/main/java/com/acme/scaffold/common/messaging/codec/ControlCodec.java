package com.acme.scaffold.common.messaging.codec;

import com.acme.scaffold.common.messaging.domain.MsgType;

/**
 * 控制帧编解码（魔数 0x41 = 'A'，版本 0x01）。
 *
 * <p>承载三种控制语义（与数据帧 0x57 分离，便于网关对控制帧走快速路径）：
 * <ul>
 *   <li>ACK：应用层确认，携带被确认的 seq（§3.3 关键消息必回）；</li>
 *   <li>PING / PONG：心跳探测（§3.3，30s 周期）。</li>
 * </ul>
 *
 * <pre>
 *  +--------+---------+---------+-------+---------------+
 *  | magic  | version | msgType | flags | zigzag64 seq  |
 *  | 0x41   | 0x01    |         | bit0  | （仅 flags=1）|
 *  +--------+---------+---------+-------+---------------+
 * </pre>
 * flags bit0=1 表示携带 seq（ACK 帧）；PING/PONG 不带。
 */
public final class ControlCodec {

    /** 控制帧魔数：'A'（Ack/Control）。 */
    public static final int MAGIC = 0x41;

    public static final int VERSION = 0x01;

    /** flags bit0：携带 zigzag64 seq（ACK 帧）。 */
    public static final byte FLAG_HAS_SEQ = 0x01;

    /** 控制帧最大编码长度。 */
    public static final int MAX_CONTROL_FRAME = 4 + Varint.MAX_VARINT_BYTES;

    private ControlCodec() {
    }

    /** 解码后的控制帧。 */
    public record ControlFrame(MsgType type, long seq) {
    }

    /** 编码 ACK 帧（seq 为被确认的数据帧序号）。 */
    public static byte[] encodeAck(long seq) {
        byte[] buf = new byte[MAX_CONTROL_FRAME];
        int pos = 0;
        buf[pos++] = (byte) MAGIC;
        buf[pos++] = (byte) VERSION;
        buf[pos++] = (byte) MsgType.ACK.code();
        buf[pos++] = FLAG_HAS_SEQ;
        pos = Varint.writeZigZag64(buf, pos, seq);
        return exactCopy(buf, pos);
    }

    /** 编码 PING 帧（服务端心跳探测）。 */
    public static byte[] encodePing() {
        return encodeBare(MsgType.PING);
    }

    /** 编码 PONG 帧（客户端心跳响应 / 服务端回环）。 */
    public static byte[] encodePong() {
        return encodeBare(MsgType.PONG);
    }

    private static byte[] encodeBare(MsgType type) {
        byte[] buf = new byte[4];
        buf[0] = (byte) MAGIC;
        buf[1] = (byte) VERSION;
        buf[2] = (byte) type.code();
        buf[3] = 0;
        return buf;
    }

    /** 通用解码；协议错误抛 {@link CorruptFrameException}。 */
    public static ControlFrame decode(byte[] frame) {
        Varint.Reader reader = new Varint.Reader(frame);
        int magic = reader.readByte();
        if (magic != MAGIC) {
            throw new CorruptFrameException("控制帧魔数不匹配: 0x" + Integer.toHexString(magic));
        }
        int version = reader.readByte();
        if (version != VERSION) {
            throw new CorruptFrameException("不支持的控制帧版本: " + version);
        }
        int typeCode = reader.readByte();
        MsgType type;
        try {
            type = MsgType.fromCode(typeCode);
        } catch (IllegalArgumentException ex) {
            throw new CorruptFrameException("未知控制帧类型: " + typeCode, ex);
        }
        int flags = reader.readByte();
        long seq = 0L;
        if ((flags & FLAG_HAS_SEQ) != 0) {
            seq = reader.readZigZag64();
        }
        reader.expectEnd();
        return new ControlFrame(type, seq);
    }

    /** 解码 ACK 帧（严格校验类型必须为 ACK）。 */
    public static long decodeAck(byte[] frame) {
        ControlFrame control = decode(frame);
        if (control.type() != MsgType.ACK) {
            throw new CorruptFrameException("控制帧类型不是 ACK: " + control.type());
        }
        return control.seq();
    }

    private static byte[] exactCopy(byte[] buf, int length) {
        byte[] exact = new byte[length];
        System.arraycopy(buf, 0, exact, 0, length);
        return exact;
    }
}
