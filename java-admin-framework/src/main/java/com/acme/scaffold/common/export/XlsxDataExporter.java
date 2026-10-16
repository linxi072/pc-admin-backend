package com.acme.scaffold.common.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 零依赖 XLSX 写出器。
 *
 * <p>XLSX 本质是 OOXML 规范定义的 ZIP 包（内含若干 XML），Excel / Numbers / LibreOffice / WPS 均可打开。
 * 本类仅用 JDK 内置 {@link ZipOutputStream} 即可生成「最小合规工作簿」，无需引入 POI 等重型依赖，
 * 适合离线构建环境与对产物体积敏感的框架基座。</p>
 *
 * <p>实现要点：
 * <ul>
 *   <li>采用 <b>inlineStr</b>（单元格内联字符串 {@code <c t="inlineStr"><is><t>...</t></is></c>}），
 *       省去 {@code sharedStrings.xml} 共享字符串表，结构最简；</li>
 *   <li>文本经 XML 转义（{@code & < > "}），避免公式注入与非法 XML；</li>
 *   <li>仅输出单工作表 {@code Sheet1}，覆盖绝大多数后台导出场景。</li>
 * </ul>
 *
 * <p>本类是纯函数工具，不依赖 Spring 容器，便于在任意导出任务与离线单测中复用。</p>
 */
public final class XlsxDataExporter {

    private static final String NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships";

    private XlsxDataExporter() {
    }

    /**
     * 将表头与行数据写出为 XLSX（最小合规工作簿，UTF-8）。
     *
     * @param headers 表头列（可为空）
     * @param rows    数据行（每行字段数建议与表头一致；为 {@code null} 或为空时仅输出表头行）
     * @return 完整 {@code .xlsx} 文件字节（ZIP 格式，以 {@code PK} 魔术字节开头）
     */
    public static byte[] export(List<String> headers, List<List<String>> rows) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.setMethod(ZipOutputStream.DEFLATED);
            writeEntry(zos, "[Content_Types].xml", contentTypesXml());
            writeEntry(zos, "_rels/.rels", rootRelsXml());
            writeEntry(zos, "xl/workbook.xml", workbookXml());
            writeEntry(zos, "xl/_rels/workbook.xml.rels", workbookRelsXml());
            writeEntry(zos, "xl/worksheets/sheet1.xml", sheetXml(headers, rows));
        } catch (IOException e) {
            // ByteArrayOutputStream 不抛 IOException，防御性保留
            throw new IllegalStateException("XLSX 生成失败", e);
        }
        return bos.toByteArray();
    }

    private static void writeEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static String contentTypesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Types xmlns=\"" + NS_PKG_REL + "\">\n"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>\n"
                + "<Override PartName=\"/xl/workbook.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n"
                + "</Types>";
    }

    private static String rootRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"" + NS_PKG_REL + "\">\n"
                + "<Relationship Id=\"rId1\" Type=\"" + NS_REL + "/officeDocument\" Target=\"xl/workbook.xml\"/>\n"
                + "</Relationships>";
    }

    private static String workbookXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<workbook xmlns=\"" + NS_MAIN + "\" xmlns:r=\"" + NS_REL + "\">\n"
                + "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets>\n"
                + "</workbook>";
    }

    private static String workbookRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"" + NS_PKG_REL + "\">\n"
                + "<Relationship Id=\"rId1\" Type=\"" + NS_REL + "/worksheet\" Target=\"worksheets/sheet1.xml\"/>\n"
                + "</Relationships>";
    }

    private static String sheetXml(List<String> headers, List<List<String>> rows) {
        int totalCols = (headers == null) ? 0 : headers.size();
        int totalRows = 1 + ((rows == null) ? 0 : rows.size());
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<worksheet xmlns=\"").append(NS_MAIN).append("\">\n");
        if (totalCols > 0 || totalRows > 0) {
            String lastCol = colLetter(Math.max(0, totalCols - 1));
            sb.append("<dimension ref=\"A1:").append(lastCol).append(totalRows).append("\"/>\n");
        }
        sb.append("<sheetData>\n");
        sb.append(rowXml(0, headers));
        if (rows != null) {
            for (int r = 0; r < rows.size(); r++) {
                sb.append(rowXml(r + 1, rows.get(r)));
            }
        }
        sb.append("</sheetData>\n");
        sb.append("</worksheet>");
        return sb.toString();
    }

    private static String rowXml(int rowIndex0, List<String> cells) {
        int excelRow = rowIndex0 + 1;
        StringBuilder sb = new StringBuilder();
        sb.append("<row r=\"").append(excelRow).append("\">");
        if (cells != null) {
            for (int c = 0; c < cells.size(); c++) {
                String ref = colLetter(c) + excelRow;
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\">")
                        .append("<is><t xml:space=\"preserve\">")
                        .append(xmlEscape(cells.get(c)))
                        .append("</t></is></c>");
            }
        }
        sb.append("</row>\n");
        return sb.toString();
    }

    /** 0 基列号转 A/B/.../Z/AA 列名。 */
    private static String colLetter(int col) {
        StringBuilder sb = new StringBuilder();
        int n = col + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.append((char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.reverse().toString();
    }

    private static String xmlEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
