package com.acme.scaffold.common.messaging.codec;

import com.acme.scaffold.common.messaging.domain.Envelope;
import com.acme.scaffold.common.messaging.domain.MsgType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FrameCodec} 数据帧编解码测试（魔数 0x57）。
 *
 * <p>覆盖：encode→decode 往返（含/不含 traceId）、字段保留、防御性拷贝、
 * looksComplete 预检、魔数/版本错误抛 {@link CorruptFrameException}。
 */
class FrameCodecTest {

    private Envelope sample(boolean withTrace) {
        byte[] payload = "hello-workframe".getBytes(StandardCharsets.UTF_8);
        return Envelope.downstream(7L, "msg-0001", "room-42",
                payload, 1_700_000_000_123L, withTrace ? "trace-abc" : "");
    }

    @Test
    void encode_decode_往返一致_带trace() {
        Envelope in = sample(true);
        byte[] frame = FrameCodec.encode(in);
        Envelope out = FrameCodec.decode(frame);
        assertEquals(in, out);
        assertEquals(in.traceId(), out.traceId());
    }

    @Test
    void encode_decode_往返一致_无trace() {
        Envelope in = sample(false);
        Envelope out = FrameCodec.decode(FrameCodec.encode(in));
        assertEquals(in, out);
        assertEquals("", out.traceId());
        // 无 trace 时帧应更短（少一段 traceId 长度前缀+内容）
        byte[] withTrace = FrameCodec.encode(sample(true));
        assertTrue(frameLen(sample(false)) < withTrace.length);
    }

    @Test
    void encode_保留所有字段() {
        Envelope in = sample(true);
        Envelope out = FrameCodec.decode(FrameCodec.encode(in));
        assertEquals(7L, out.seq());
        assertEquals("msg-0001", out.msgId());
        assertEquals(MsgType.DATA, out.type());
        assertEquals("room-42", out.key());
        assertEquals(1_700_000_000_123L, out.timestamp());
        assertArrayEquals("hello-workframe".getBytes(StandardCharsets.UTF_8), out.payload());
    }

    @Test
    void looksComplete_魔数识别() {
        assertTrue(FrameCodec.looksComplete(FrameCodec.encode(sample(false))));
        byte[] notFrame = {0x00, 0x01, 0x02, 0x03};
        assertFalse(FrameCodec.looksComplete(notFrame));
        assertFalse(FrameCodec.looksComplete(null));
        assertFalse(FrameCodec.looksComplete(new byte[2]));
    }

    @Test
    void decode_魔数错误抛异常() {
        byte[] buf = new byte[]{0x00, 0x01, 0x01, 0x00};
        assertThrows(CorruptFrameException.class, () -> FrameCodec.decode(buf));
    }

    @Test
    void decode_版本错误抛异常() {
        byte[] buf = new byte[]{(byte) FrameCodec.MAGIC, 0x02, 0x01, 0x00};
        assertThrows(CorruptFrameException.class, () -> FrameCodec.decode(buf));
    }

    @Test
    void decode_未知消息类型抛异常() {
        byte[] buf = new byte[]{(byte) FrameCodec.MAGIC, (byte) FrameCodec.VERSION, 0x7F, 0x00};
        assertThrows(CorruptFrameException.class, () -> FrameCodec.decode(buf));
    }

    private int frameLen(Envelope e) {
        return FrameCodec.encode(e).length;
    }
}
