package com.acme.scaffold.common.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 零依赖 PDF 导出器单测（E5）：结构合法性、分页、UCS2 中文编码、转义与注入防护。 */
class PdfExporterTest {

    @Test
    void shouldProduceValidPdfHeaderAndTrailer() {
        byte[] bytes = PdfExporter.export(List.of("编号", "Name"), List.of(List.of("1", "Alice")));
        String pdf = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(pdf.startsWith("%PDF-1.4"));
        assertTrue(pdf.endsWith("%%EOF\n"));
        assertTrue(pdf.contains("/Type /Catalog"));
        assertTrue(pdf.contains("/Type /Pages /Kids [7 0 R] /Count 1"));
        assertTrue(pdf.contains("/BaseFont /STSong-Light"));
        assertTrue(pdf.contains("/Encoding /UniGB-UCS2-H"));
        assertTrue(pdf.contains("<FEFF7F1653F7")); // “编号” 的 UCS2-BE 编码
        assertTrue(pdf.contains("(Alice) Tj"));
        assertTrue(pdf.contains("xref\n0 "));
        assertEquals(1, count(pdf, "/Type /Page "));
    }

    @Test
    void shouldRepeatHeaderOnEveryPage() {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            rows.add(List.of("行" + i, "value-" + i));
        }
        byte[] bytes = PdfExporter.export(List.of("序号", "值"), rows, "导出标题");
        String pdf = new String(bytes, StandardCharsets.ISO_8859_1);
        int pageCount = count(pdf, "/Type /Page ");
        assertTrue(pageCount >= 5, "实际页数=" + pageCount);
        assertTrue(pdf.contains("/Count " + pageCount));
        assertEquals(pageCount, count(pdf, "<FEFF5E8F53F7")); // “序号” 在每页重复
        assertTrue(pdf.contains("<FEFF5BFC51FA")); // 标题只出现在首页一次
        assertEquals(1, count(pdf, "<FEFF5BFC51FA"));
    }

    @Test
    void shouldEscapeLiteralAndNeutralizeFormula() {
        byte[] bytes = PdfExporter.export(List.of("备注"), List.of(List.of("a(b)\\c"), List.of("=1+1")));
        String pdf = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(pdf.contains("a\\(b\\)\\\\c"));
        assertTrue(pdf.contains("'=1+1"));
    }

    @Test
    void shouldHandleNullAndEmptyCells() {
        byte[] bytes = PdfExporter.export(List.of("A", "B"), List.of(java.util.Arrays.asList(null, "")));
        assertTrue(bytes.length > 200);
        assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains("null"));
    }

    private static int count(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
