package com.acme.scaffold.common.export;

import com.acme.scaffold.common.importer.TabularData;
import com.acme.scaffold.common.importer.XlsxDataReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    /** 解包多 Sheet 产物为「条目名 → 内容」映射。 */
    private static Map<String, String> unzipAll(byte[] bytes) throws IOException {
        Map<String, String> parts = new java.util.LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                parts.put(e.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return parts;
    }

    @Test
    void exportWorkbook_producesMultiSheetWorkbookWithStyles() throws IOException {
        byte[] bytes = XlsxDataExporter.exportWorkbook(List.of(
                new XlsxSheet("汇总", List.of("指标", "数值"), List.of(List.of("访问量", "100"))),
                new XlsxSheet("趋势", List.of("时间", "访问量"), List.of(List.of("10-15", "30")))));
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);

        Map<String, String> parts = unzipAll(bytes);
        assertTrue(parts.containsKey("xl/styles.xml"), "多 Sheet 产物须含样式表");
        assertTrue(parts.containsKey("xl/worksheets/sheet1.xml"));
        assertTrue(parts.containsKey("xl/worksheets/sheet2.xml"), "两张工作表对应 sheet2.xml");
        assertTrue(parts.get("xl/workbook.xml").contains("汇总"), "workbook.xml 登记首表名");
        assertTrue(parts.get("xl/workbook.xml").contains("趋势"), "workbook.xml 登记次表名");
        // 表头行套样式 s="1"（加粗灰底），数据行无样式索引
        assertTrue(parts.get("xl/worksheets/sheet1.xml").contains("s=\"1\""), "表头单元格套表头样式");
        // 列宽自适应声明
        assertTrue(parts.get("xl/worksheets/sheet1.xml").contains("<cols>"), "声明自适应列宽");
        // XML 转义在多 Sheet 路径同样生效
        assertTrue(parts.get("xl/worksheets/sheet2.xml").contains("访问量"));
    }

    @Test
    void exportWorkbook_normalizesIllegalDuplicateAndLongNames() throws IOException {
        byte[] bytes = XlsxDataExporter.exportWorkbook(List.of(
                new XlsxSheet("A/B", List.of("h"), List.of()),
                new XlsxSheet("A:B", List.of("h"), List.of()),
                new XlsxSheet("重复".repeat(20), List.of("h"), List.of())));
        String workbook = unzipAll(bytes).get("xl/workbook.xml");
        assertTrue(workbook.contains("A_B"), "非法字符 / 清洗为下划线");
        assertTrue(workbook.contains("A_B_2"), "重名工作表追加序号去重");
        assertTrue(workbook.contains("重复".repeat(10)), "超长名截断至 31 字符内仍保留前缀");
    }

    @Test
    void exportWorkbook_rejectsEmptySheetList() {
        assertThrows(IllegalArgumentException.class, () -> XlsxDataExporter.exportWorkbook(List.of()));
        assertThrows(IllegalArgumentException.class, () -> XlsxDataExporter.exportWorkbook(null));
        assertThrows(IllegalArgumentException.class,
                () -> new XlsxSheet(" ", List.of("h"), List.of()), "空白表名拒绝");
    }

    @Test
    void exportWorkbook_firstSheetRoundTripsViaReader() {
        byte[] bytes = XlsxDataExporter.exportWorkbook(List.of(
                new XlsxSheet("汇总", List.of("指标", "数值"), List.of(List.of("访问量", "100"))),
                new XlsxSheet("趋势", List.of("时间"), List.of(List.of("10-15")))));
        // 导入端取第一个工作表：多 Sheet 产物首表仍为 sheet1.xml，可直接读回
        TabularData data = XlsxDataReader.read(bytes);
        assertEquals(List.of("指标", "数值"), data.headers());
        assertEquals(1, data.rowCount());
        assertEquals(List.of("访问量", "100"), data.rows().get(0));
    }
}
