package com.acme.scaffold.common.messaging.domain;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link Envelope} 消息信封测试（不可变 + 构造期强校验 + 防御性拷贝）。
 *
 * <p>覆盖：upstream/downstream 工厂、字段校验（msgId/key/timestamp 边界）、withSeq 副本、
 * payload/keyBytes 防御性拷贝、equals/hashCode。
 */
class EnvelopeTest {

    @Test
    void upstream_seq未分配_data类型() {
        Envelope e = Envelope.upstream("m1", "k1", "p".getBytes(StandardCharsets.UTF_8), 100L, "");
        assertEquals(Envelope.SEQ_UNASSIGNED, e.seq());
        assertEquals(MsgType.DATA, e.type());
        assertEquals("m1", e.msgId());
    }

    @Test
    void downstream_携带seq() {
        Envelope e = Envelope.downstream(5L, "m2", "k2", new byte[0], 200L, "t");
        assertEquals(5L, e.seq());
        assertEquals("t", e.traceId());
    }

    @Test
    void 构造_空msgId抛异常() {
        assertThrows(IllegalArgumentException.class,
                () -> new Envelope(0, "", MsgType.DATA, "k", new byte[0], 1L, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new Envelope(0, "   ", MsgType.DATA, "k", new byte[0], 1L, ""));
    }

    @Test
    void 构造_空key抛异常() {
        assertThrows(IllegalArgumentException.class,
                () -> new Envelope(0, "m", MsgType.DATA, "", new byte[0], 1L, ""));
    }

    @Test
    void 构造_非正timestamp抛异常() {
        assertThrows(IllegalArgumentException.class,
                () -> new Envelope(0, "m", MsgType.DATA, "k", new byte[0], 0L, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new Envelope(0, "m", MsgType.DATA, "k", new byte[0], -1L, ""));
    }

    @Test
    void 构造_msgId超长抛异常() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            sb.append('x');
        }
        String tooLong = sb.toString();
        assertThrows(IllegalArgumentException.class,
                () -> new Envelope(0, tooLong, MsgType.DATA, "k", new byte[0], 1L, ""));
    }

    @Test
    void 构造_key超长抛异常() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 257; i++) {
            sb.append('x');
        }
        assertThrows(IllegalArgumentException.class,
                () -> new Envelope(0, "m", MsgType.DATA, sb.toString(), new byte[0], 1L, ""));
    }

    @Test
    void withSeq_返回新副本() {
        Envelope e = Envelope.downstream(1L, "m", "k", new byte[]{9}, 1L, "");
        Envelope next = e.withSeq(2L);
        assertEquals(2L, next.seq());
        assertEquals(1L, e.seq()); // 原对象不变
        assertEquals(e.msgId(), next.msgId());
    }

    @Test
    void payload_防御性拷贝() {
        byte[] original = {1, 2, 3};
        Envelope e = Envelope.downstream(1L, "m", "k", original, 1L, "");
        original[0] = 99; // 外部修改不应影响信封
        assertArrayEquals(new byte[]{1, 2, 3}, e.payload());
        byte[] fetched = e.payload();
        fetched[0] = 42; // 取回后修改也不应影响信封
        assertArrayEquals(new byte[]{1, 2, 3}, e.payload());
        assertNotSame(e.payload(), e.payload());
    }

    @Test
    void keyBytes_UTF8编码() {
        Envelope e = Envelope.downstream(1L, "m", "房间-42", new byte[0], 1L, "");
        assertArrayEquals("房间-42".getBytes(StandardCharsets.UTF_8), e.keyBytes());
    }

    @Test
    void equals_hashCode_基于值() {
        Envelope a = Envelope.downstream(1L, "m", "k", new byte[]{1}, 1L, "t");
        Envelope b = Envelope.downstream(1L, "m", "k", new byte[]{1}, 1L, "t");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        Envelope c = Envelope.downstream(2L, "m", "k", new byte[]{1}, 1L, "t");
        assertNotEquals(a, c);
    }
}
