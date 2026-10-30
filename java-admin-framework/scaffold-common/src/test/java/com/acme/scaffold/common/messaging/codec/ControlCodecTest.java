package com.acme.scaffold.common.messaging.codec;

import com.acme.scaffold.common.messaging.domain.MsgType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ControlCodec} 控制帧编解码测试（魔数 0x41）。
 *
 * <p>覆盖：ACK 编码/解码 seq 往返、PING/PONG 类型识别、decodeAck 严格校验类型、
 * 魔数错误抛 {@link CorruptFrameException}。
 */
class ControlCodecTest {

    @Test
    void encodeAck_decodeAck_seq往返() {
        for (long seq : new long[]{0L, 1L, 42L, 9_999_999L, Long.MAX_VALUE}) {
            byte[] frame = ControlCodec.encodeAck(seq);
            assertEquals(seq, ControlCodec.decodeAck(frame));
            ControlCodec.ControlFrame cf = ControlCodec.decode(frame);
            assertEquals(MsgType.ACK, cf.type());
            assertEquals(seq, cf.seq());
        }
    }

    @Test
    void encodePing_decode_类型识别() {
        ControlCodec.ControlFrame cf = ControlCodec.decode(ControlCodec.encodePing());
        assertEquals(MsgType.PING, cf.type());
        assertEquals(0L, cf.seq());
    }

    @Test
    void encodePong_decode_类型识别() {
        ControlCodec.ControlFrame cf = ControlCodec.decode(ControlCodec.encodePong());
        assertEquals(MsgType.PONG, cf.type());
        assertEquals(0L, cf.seq());
    }

    @Test
    void decodeAck_非ACK帧抛异常() {
        byte[] ping = ControlCodec.encodePing();
        assertThrows(CorruptFrameException.class, () -> ControlCodec.decodeAck(ping));
    }

    @Test
    void decode_魔数错误抛异常() {
        byte[] buf = new byte[]{0x00, 0x01, (byte) MsgType.ACK.code(), ControlCodec.FLAG_HAS_SEQ};
        assertThrows(CorruptFrameException.class, () -> ControlCodec.decode(buf));
    }
}
