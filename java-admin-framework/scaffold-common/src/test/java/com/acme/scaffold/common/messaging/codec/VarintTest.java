package com.acme.scaffold.common.messaging.codec;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link Varint} 原语测试（LEB128 无符号变长 + ZigZag 有符号映射 + {@link Varint.Reader} 游标）。
 *
 * <p>覆盖：写读往返、边界值（0/1/127/128/MAX）、ZigZag 正负映射、长度计算、截断/越界抛
 * {@link CorruptFrameException}、字符串/字节段读写、尾部垃圾拒绝。
 */
class VarintTest {

    @Test
    void writeVarint_readVarint_往返一致() {
        long[] values = {0L, 1L, 127L, 128L, 300L, 16384L, Integer.MAX_VALUE,
                Long.MAX_VALUE, -1L /*无符号位模式*/, 0x7FFFFFFFFFFFFFFFL};
        for (long v : values) {
            byte[] buf = new byte[Varint.MAX_VARINT_BYTES];
            int end = Varint.writeVarint(buf, 0, v);
            assertEquals(v, new Varint.Reader(buf, 0, end).readVarint(), "varint 往返失败: " + v);
        }
    }

    @Test
    void varintSize_与编码长度一致() {
        for (long v : new long[]{0, 1, 127, 128, 16383, 16384, Long.MAX_VALUE}) {
            byte[] buf = new byte[Varint.MAX_VARINT_BYTES];
            int end = Varint.writeVarint(buf, 0, v);
            assertEquals(end, Varint.varintSize(v));
        }
    }

    @Test
    void zigZag64_正负对称往返() {
        long[] values = {0L, 1L, -1L, 2L, -2L, 63L, -63L, Long.MAX_VALUE, Long.MIN_VALUE};
        for (long v : values) {
            long encoded = Varint.encodeZigZag64(v);
            assertEquals(v, Varint.decodeZigZag64(encoded), "zigzag 往返失败: " + v);
        }
    }

    @Test
    void zigZag64_映射值符合约定() {
        assertEquals(0L, Varint.encodeZigZag64(0L));
        assertEquals(1L, Varint.encodeZigZag64(-1L));
        assertEquals(2L, Varint.encodeZigZag64(1L));
        assertEquals(3L, Varint.encodeZigZag64(-2L));
        // Long.MIN_VALUE 经 (v<<1)^(v>>63) 后为 -1（无符号位模式）
        assertEquals(-1L, Varint.encodeZigZag64(Long.MIN_VALUE));
        assertEquals(Long.MIN_VALUE, Varint.decodeZigZag64(-1L));
    }

    @Test
    void writeZigZag64_readZigZag64_往返一致() {
        byte[] buf = new byte[Varint.MAX_VARINT_BYTES];
        for (long v : new long[]{0, -1, 1, -123456789L, 123456789L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            int end = Varint.writeZigZag64(buf, 0, v);
            assertEquals(v, new Varint.Reader(buf, 0, end).readZigZag64());
        }
    }

    @Test
    void reader_读取字符串与字节段() {
        String s = "room-10086";
        byte[] sBytes = s.getBytes(StandardCharsets.UTF_8);
        byte[] payload = {1, 2, 3, 4, 5};
        byte[] buf = new byte[64];
        int p = Varint.writeVarint(buf, 0, sBytes.length);
        System.arraycopy(sBytes, 0, buf, p, sBytes.length);
        p += sBytes.length;
        p = Varint.writeVarint(buf, p, payload.length);
        System.arraycopy(payload, 0, buf, p, payload.length);
        p += payload.length;

        Varint.Reader reader = new Varint.Reader(buf, 0, p);
        assertEquals(s, reader.readString(64));
        long payloadLen = reader.readVarint();
        assertEquals(payload.length, payloadLen);
        assertArrayEquals(payload, reader.readBytes((int) payloadLen));
        reader.expectEnd();
    }

    @Test
    void reader_截断抛CorruptFrameException() {
        // 0x80 表示还有后续字节，但缓冲区到此结束
        byte[] truncated = {(byte) 0x80};
        Varint.Reader reader = new Varint.Reader(truncated);
        assertThrows(CorruptFrameException.class, reader::readVarint);
    }

    @Test
    void reader_尾部垃圾拒绝() {
        byte[] buf = new byte[8];
        int end = Varint.writeVarint(buf, 0, 42L);
        buf[end] = 0x7F; // 额外的尾部字节
        Varint.Reader reader = new Varint.Reader(buf, 0, end + 1);
        assertEquals(42L, reader.readVarint());
        assertThrows(CorruptFrameException.class, reader::expectEnd);
    }

    @Test
    void reader_窗口越界抛异常() {
        byte[] buf = {1, 2, 3};
        assertThrows(IllegalArgumentException.class, () -> new Varint.Reader(buf, 1, 5));
        // 三参构造内含 null 校验（单参构造委托前即解引用会 NPE，故走三参路径验证契约）
        assertThrows(IllegalArgumentException.class, () -> new Varint.Reader(null, 0, 0));
    }
}
