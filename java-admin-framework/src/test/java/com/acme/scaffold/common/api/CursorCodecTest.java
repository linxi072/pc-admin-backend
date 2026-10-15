package com.acme.scaffold.common.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorCodecTest {

    @Test
    void roundTrip_mixedTypes() {
        List<?> values = List.of(123L, "2026-10-15T21:01:28", "中文文件名.txt");
        String cursor = CursorCodec.encode(values);
        assertFalse(cursor.isBlank());

        List<String> decoded = CursorCodec.decode(cursor);
        assertEquals(3, decoded.size());
        assertEquals("123", decoded.get(0));
        assertEquals("2026-10-15T21:01:28", decoded.get(1));
        assertEquals("中文文件名.txt", decoded.get(2));
    }

    @Test
    void emptyAndNullCursor_isEmptyList() {
        assertEquals("", CursorCodec.encode(List.of()));
        assertTrue(CursorCodec.decode("").isEmpty());
        assertTrue(CursorCodec.decode(null).isEmpty());
    }

    @Test
    void invalidCursor_isEmptyList_notThrown() {
        assertTrue(CursorCodec.decode("!!!not-base64!!!").isEmpty());
    }

    @Test
    void differentValues_produceDifferentCursor() {
        String a = CursorCodec.encode(List.of(1L, "x"));
        String b = CursorCodec.encode(List.of(2L, "x"));
        assertNotEquals(a, b);
    }

    @Test
    void valueWithCommonDelimiters_roundTrips() {
        // 单元分隔符（\u0001）不会出现在真实数据里，验证含逗号/竖线/空格仍正确还原
        String v = "a,b|c d";
        List<String> decoded = CursorCodec.decode(CursorCodec.encode(List.of(v)));
        assertEquals(1, decoded.size());
        assertEquals(v, decoded.get(0));
    }
}
