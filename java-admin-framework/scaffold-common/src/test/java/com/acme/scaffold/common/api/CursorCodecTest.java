package com.acme.scaffold.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CursorCodec 纯函数单测：固定 keyset 游标编解码契约（V1.1.0 第三轮优化）。
 * 任何对分隔符 / Base64 编码方式的改动都会导致本测试失败，防止游标格式被无意变更
 * 造成线上翻页失效。
 */
class CursorCodecTest {

    @Test
    void emptyListShouldEncodeToEmptyString() {
        assertEquals("", CursorCodec.encode(List.of()));
        assertEquals("", CursorCodec.encode(null));
    }

    @Test
    void singleValueShouldRoundTrip() {
        String cursor = CursorCodec.encode(List.of(123L));
        assertEquals(List.of("123"), CursorCodec.decode(cursor));
    }

    @Test
    void multipleValuesShouldRoundTripInOrder() {
        List<String> values = List.of("100", "2026-11-23T09:00:00", "alpha");
        String cursor = CursorCodec.encode(values);
        assertEquals(values, CursorCodec.decode(cursor));
    }

    @Test
    void nullValueShouldEncodeAsEmptySegment() {
        // null 值编码为空段，解码后仍保留该段（保持键数量，供 KeysetPager 按位还原）
        String cursor = CursorCodec.encode(Arrays.asList(1L, null, 3L));
        assertEquals(Arrays.asList("1", "", "3"), CursorCodec.decode(cursor));
    }

    @Test
    void emptyAndBlankCursorShouldDecodeToEmptyList() {
        assertEquals(List.of(), CursorCodec.decode(null));
        assertEquals(List.of(), CursorCodec.decode(""));
        assertEquals(List.of(), CursorCodec.decode("   "));
    }

    @Test
    void invalidBase64ShouldSilentlyDegradeToFirstPage() {
        // 非法 base64 游标静默降级为首页（空列表），不抛异常——契约固定
        assertEquals(List.of(), CursorCodec.decode("!!!not-base64!!!"));
        assertEquals(List.of(), CursorCodec.decode("###"));
    }

    @Test
    void encodedCursorShouldBeUrlSafe() {
        String cursor = CursorCodec.encode(List.of("a/b+c=1&2", "中文"));
        // base64url 字符集不含 + / = 等需转义字符
        assertTrue(cursor.matches("[A-Za-z0-9_-]+"), "游标必须是 URL 安全的 base64url 串: " + cursor);
        assertEquals(List.of("a/b+c=1&2", "中文"), CursorCodec.decode(cursor), "特殊字符与非 ASCII 应无损往返");
    }
}
