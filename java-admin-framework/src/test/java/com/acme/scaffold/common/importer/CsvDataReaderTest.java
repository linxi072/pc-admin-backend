package com.acme.scaffold.common.importer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯函数测试：CSV 读取器（RFC4180 引号语义、BOM、行分隔兼容、列宽补齐）。
 */
class CsvDataReaderTest {

    private static TabularData read(String text) {
        return CsvDataReader.read(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void stripsUtf8BomFromFirstHeader() {
        // 本框架导出的 CSV 带 BOM；往返导入时 BOM 不得混入首列表头
        byte[] withBom = ("\uFEFF" + "类型,标签\nA,B\n").getBytes(StandardCharsets.UTF_8);
        TabularData data = CsvDataReader.read(withBom);
        assertEquals(List.of("类型", "标签"), data.headers());
        assertEquals(1, data.rowCount());
    }

    @Test
    void parsesQuotedFieldsWithCommasQuotesAndNewlines() {
        TabularData quoted = read("a,b\n\"x,y\",\"he said \"\"hi\"\"\"\n");
        assertEquals(List.of("x,y", "he said \"hi\""), quoted.rows().get(0));

        // 引号内的换行不视为行分隔
        TabularData multiline = read("a,b\n\"line1\nline2\",z\n");
        assertEquals(1, multiline.rowCount());
        assertEquals(List.of("line1\nline2", "z"), multiline.rows().get(0));
    }

    @Test
    void supportsCrlfLfAndLoneCr() {
        assertEquals(2, read("a,b\r\n1,2\r\n3,4\r\n").rowCount());
        assertEquals(2, read("a,b\n1,2\n3,4\n").rowCount());
        // 单独 CR：Excel 在部分平台另存为 CSV 时的行分隔符
        assertEquals(2, read("a,b\r1,2\r3,4\r").rowCount());
    }

    @Test
    void ignoresTrailingBlankLine() {
        // 文件以单个换行结尾属正常情况，不应产生额外数据行
        assertEquals(1, read("a,b\n1,2\n").rowCount());
    }

    @Test
    void keepsBlankLinesForPositionalFidelity() {
        // 真实的空行予以保留（不与相邻行合并），保证后续行号仍与文件物理行一致；
        // 空行由导入中心跳过，不影响统计。
        TabularData data = read("a,b\n1,2\n\n3,4\n");
        assertEquals(3, data.rowCount());
        assertEquals(List.of("1", "2"), data.rows().get(0));
        assertEquals(List.of("", ""), data.rows().get(1));
        assertEquals(List.of("3", "4"), data.rows().get(2));
    }

    @Test
    void padsRaggedRowsToUniformWidth() {
        TabularData data = read("a,b,c\n1\n2,3,4\n");
        assertEquals(3, data.columnCount());
        assertEquals(List.of("1", "", ""), data.rows().get(0), "缺失的尾部单元格应补空串");
        assertEquals(List.of("2", "3", "4"), data.rows().get(1));
    }

    @Test
    void preservesTrailingEmptyCellFromTrailingComma() {
        TabularData data = read("a,b\n1,\n");
        assertEquals(2, data.columnCount());
        assertEquals(List.of("1", ""), data.rows().get(0));
    }

    @Test
    void emptyOrNullInputYieldsEmptyTable() {
        assertTrue(CsvDataReader.read(null).isEmpty());
        assertTrue(CsvDataReader.read(new byte[0]).isEmpty());
        assertTrue(read("").isEmpty());
    }
}
