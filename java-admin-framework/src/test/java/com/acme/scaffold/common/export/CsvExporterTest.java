package com.acme.scaffold.common.export;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CsvExporter} 单元测试：UTF-8 BOM 前缀、RFC4180 转义、CRLF 行分隔。
 * 不依赖数据库或 Spring 容器，纯函数离线验证。
 */
class CsvExporterTest {

    @Test
    void writesUtf8BomPrefix() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        CsvExporter.write(bos, List.of("a"), List.of(List.of("x")));
        byte[] bytes = bos.toByteArray();
        // UTF-8 BOM = EF BB BF
        assertArrayEquals(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF },
                new byte[] { bytes[0], bytes[1], bytes[2] });
    }

    @Test
    void escapesCommaQuoteAndNewline() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        CsvExporter.write(bos,
                List.of("name", "note"),
                List.of(List.of("a,b", "he said \"hi\"\ncq"), List.of("plain", "ok")));
        String csv = bos.toString(StandardCharsets.UTF_8.name());
        // 首行是 BOM + name,note\r\n
        assertTrue(csv.contains("name,note\r\n"));
        // 含逗号与换行的字段应被双引号包裹
        assertTrue(csv.contains("\"a,b\""));
        // 内部双引号转义为两个双引号；字段内换行保持 LF，行尾才是 CRLF
        assertTrue(csv.contains("\"he said \"\"hi\"\"\ncq\""));
        assertTrue(csv.contains("plain,ok\r\n"));
    }

    @Test
    void handlesNullAndEmptyCells() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        CsvExporter.write(bos, List.of("c1", "c2"), List.of(Arrays.asList(null, "")));
        String csv = bos.toString(StandardCharsets.UTF_8.name());
        // 两空单元格：单逗号分隔，即 "c1,c2\r\n,\r\n"
        assertEquals("﻿c1,c2\r\n,\r\n", csv);
    }
}
