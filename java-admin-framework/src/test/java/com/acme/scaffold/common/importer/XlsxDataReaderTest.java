package com.acme.scaffold.common.importer;

import com.acme.scaffold.common.export.XlsxDataExporter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯函数测试：零依赖 XLSX 读取器（与 {@link XlsxDataExporter} 构成闭环）。
 */
class XlsxDataReaderTest {

    @Test
    void roundTripsWhatExporterWrote() {
        List<String> headers = List.of("字典类型", "字典标签", "字典值");
        List<List<String>> rows = List.of(
                List.of("user_status", "启用", "ACTIVE"),
                // 含 XML 特殊字符：写出时转义、读取时应还原
                List.of("A&B", "<x>", "\"quoted\""));

        TabularData data = XlsxDataReader.read(XlsxDataExporter.export(headers, rows));

        assertEquals(headers, data.headers());
        assertEquals(2, data.rowCount());
        assertEquals(rows, data.rows());
    }

    @Test
    void handlesHeaderOnlyWorkbook() {
        TabularData data = XlsxDataReader.read(XlsxDataExporter.export(List.of("a", "b"), List.of()));
        assertEquals(List.of("a", "b"), data.headers());
        assertEquals(0, data.rowCount());
    }

    @Test
    void alignsColumnsByCellReference() {
        // 手工构造稀疏行：A1 有值、B1 空缺、C1 有值。
        // Excel 会跳过空单元格不写 <c>，因此必须按 r 属性（如 C1）定位列，而非按出现顺序。
        byte[] xlsx = zip(parts(sheetXml(
                "<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>h1</t></is></c>"
                        + "<c r=\"C1\" t=\"inlineStr\"><is><t>h3</t></is></c></row>"
                        + "<row r=\"2\"><c r=\"B2\" t=\"inlineStr\"><is><t>v2</t></is></c></row>")));

        TabularData data = XlsxDataReader.read(xlsx);

        assertEquals(List.of("h1", "", "h3"), data.headers());
        assertEquals(1, data.rowCount());
        assertEquals(List.of("", "v2", ""), data.rows().get(0));
    }

    @Test
    void resolvesSharedStringsIncludingRichText() {
        String shared = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<si><t>类型</t></si>"
                // 富文本会拆成多个 <t>，应拼接为一条
                + "<si><r><t>共享</t></r><r><t>串</t></r></si>"
                + "</sst>";
        Map<String, String> parts = parts(sheetXml(
                "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>"
                        + "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>1</v></c><c r=\"B2\"><v>42</v></c></row>"));
        parts.put("xl/sharedStrings.xml", shared);

        TabularData data = XlsxDataReader.read(zip(parts));

        assertEquals(List.of("类型", "共享串"), data.headers());
        assertEquals(List.of("共享串", "42"), data.rows().get(0), "数值单元格应取其缓存值文本");
    }

    @Test
    void rejectsNonOoxmlBytes() {
        byte[] notZip = "这不是一个 xlsx 文件".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> XlsxDataReader.read(notZip));

        byte[] zipWithoutSheet = zip(new LinkedHashMap<>(Map.of("xl/workbook.xml", "<workbook/>")));
        assertThrows(IllegalArgumentException.class, () -> XlsxDataReader.read(zipWithoutSheet));
    }

    @Test
    void emptyInputYieldsEmptyTable() {
        assertTrue(XlsxDataReader.read(null).isEmpty());
        assertTrue(XlsxDataReader.read(new byte[0]).isEmpty());
    }

    private static Map<String, String> parts(String sheetXml) {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("xl/worksheets/sheet1.xml", sheetXml);
        return parts;
    }

    private static String sheetXml(String rowsXml) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetData>" + rowsXml + "</sheetData></worksheet>";
    }

    private static byte[] zip(Map<String, String> parts) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, String> e : parts.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException("测试用 ZIP 构造失败", e);
        }
        return bos.toByteArray();
    }
}
