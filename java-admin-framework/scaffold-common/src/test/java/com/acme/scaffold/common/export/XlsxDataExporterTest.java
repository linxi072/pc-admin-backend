package com.acme.scaffold.common.export;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

/** XLSX 写出单测：非法 C0 控制字符不得污染 sheet XML，合法文本仍需保留。 */
class XlsxDataExporterTest {

    @Test
    void exportShouldProduceWellFormedXmlAfterSanitizingControlChars() throws Exception {
        String dirty = "a\u0001b\u000Bc\u001Fd\te\nf\rg & < > \" 中文";
        byte[] bytes = XlsxDataExporter.export(
                List.of("表\u0002头"), List.of(List.of(dirty)));

        assertAllXmlPartsWellFormed(bytes);
        String sheet = readEntry(bytes, "xl/worksheets/sheet1.xml");
        for (char c : new char[]{'\u0001', '\u0002', '\u000B', '\u001F'}) {
            assertFalse(sheet.indexOf(c) >= 0, "非法控制字符不应进入 XML");
        }
        assertTrue(sheet.contains("&amp;"));
        assertTrue(sheet.contains("&lt;"));
        assertTrue(sheet.contains("&quot;"));
        assertTrue(sheet.contains("中文"));
    }

    @Test
    void exportWorkbookShouldAlsoSanitizeEverySheet() throws Exception {
        XlsxSheet first = new XlsxSheet("汇总", List.of("名称"),
                List.of(List.of("ok\u0003bad")));
        XlsxSheet second = new XlsxSheet("明细", List.of("备注"),
                List.of(List.of("line1\nline2\tend")));

        byte[] bytes = XlsxDataExporter.exportWorkbook(List.of(first, second));

        assertAllXmlPartsWellFormed(bytes);
        assertFalse(readEntry(bytes, "xl/worksheets/sheet1.xml").indexOf('\u0003') >= 0);
        assertTrue(readEntry(bytes, "xl/worksheets/sheet2.xml").contains("line1\nline2\tend"));
    }

    private static void assertAllXmlPartsWellFormed(byte[] xlsx) throws Exception {
        boolean parsed = false;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.endsWith(".xml") && !name.endsWith(".rels")) {
                    continue;
                }
                byte[] part = zis.readAllBytes();
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.newDocumentBuilder().parse(new ByteArrayInputStream(part));
                parsed = true;
            }
        }
        assertTrue(parsed, "XLSX 包应包含可解析的 XML part");
    }

    private static String readEntry(byte[] xlsx, String target) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (target.equals(entry.getName())) {
                    return new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("缺失 ZIP entry: " + target);
    }
}
