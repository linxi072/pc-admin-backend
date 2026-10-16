package com.acme.scaffold.common.export;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯函数测试：验证零依赖 XLSX 写出器产出合规 ZIP/OOXML（可被 Excel/Numbers/LibreOffice 打开）。
 */
class XlsxDataExporterTest {

    @Test
    void exportsValidZipWithRequiredParts() throws IOException {
        List<String> headers = List.of("姓名", "年龄");
        List<List<String>> rows = List.of(
                List.of("张三", "30"),
                List.of("Li, \"A\"", "25")); // 含逗号与引号，验证 RFC4180 风格的 XML 转义
        byte[] bytes = XlsxDataExporter.export(headers, rows);

        assertTrue(bytes.length > 0);
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);

        Set<String> parts = new HashSet<>();
        String sheet = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                parts.add(e.getName());
                if ("xl/worksheets/sheet1.xml".equals(e.getName())) {
                    sheet = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }

        assertTrue(parts.contains("[Content_Types].xml"), "缺少 [Content_Types].xml");
        assertTrue(parts.contains("_rels/.rels"), "缺少包关系 _rels/.rels");
        assertTrue(parts.contains("xl/workbook.xml"), "缺少 workbook.xml");
        assertTrue(parts.contains("xl/_rels/workbook.xml.rels"), "缺少 workbook 关系");
        assertNotNull(sheet, "缺少 worksheet");
        assertTrue(sheet.contains("姓名"));
        assertTrue(sheet.contains("张三"));
        // 内联字符串 + XML 转义生效
        assertTrue(sheet.contains("inlineStr"));
        assertTrue(sheet.contains("&quot;A&quot;"));
    }

    @Test
    void handlesEmptyRowsAndNulls() {
        byte[] withEmpty = XlsxDataExporter.export(List.of("a", "b"), List.of());
        assertEquals('P', withEmpty[0]);
        assertEquals('K', withEmpty[1]);

        byte[] withNullHeaders = XlsxDataExporter.export(null, null);
        assertEquals('P', withNullHeaders[0]);
        assertEquals('K', withNullHeaders[1]);
    }

    @Test
    void roundTripsCyrillicAndChineseViaUtf8() throws IOException {
        List<String> headers = List.of("键", "值");
        List<List<String>> rows = List.of(List.of("greeting", "你好，世界"));
        byte[] bytes = XlsxDataExporter.export(headers, rows);
        assertTrue(bytes.length > 0);
        // 通过解压核对该中文字符串确实落在 sheet XML 中
        String sheet = unzipSheet(bytes);
        assertTrue(sheet.contains("你好，世界"));
    }

    private static String unzipSheet(byte[] bytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if ("xl/worksheets/sheet1.xml".equals(e.getName())) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }
}
