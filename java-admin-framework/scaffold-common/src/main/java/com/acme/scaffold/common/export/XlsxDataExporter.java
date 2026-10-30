package com.acme.scaffold.common.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 *   <li>{@link #export} 输出单工作表 {@code Sheet1}（无样式最小产物，与导入端
 *       {@code XlsxDataReader} 往返字节兼容）；</li>
 *   <li>{@link #exportWorkbook} 输出多 Sheet 工作簿（表头加粗灰底样式 + 列宽自适应），
 *       服务于多段结构报表（标题/汇总/分布/明细/趋势各占一页），同样零依赖。</li>
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

    /**
     * 将多个工作表写出一个多 Sheet 工作簿（UTF-8，表头行套加粗 + 灰底样式，列宽自适应）。
     *
     * <p>相比 {@link #export} 多出 {@code xl/styles.xml} 部件与逐表
     * {@code <cols>} 列宽声明；工作表文件按传入顺序命名为 {@code sheet1.xml..sheetN.xml}，
     * 首表即 {@code sheet1.xml}，导入端 {@code XlsxDataReader}（取第一个工作表）可直接读回。
     * 行数上限校验由调用方（导出中心）负责，本类不做业务约束。</p>
     *
     * @param sheets 工作表列表（至少一个；重名 / 非法字符 / 超长名在写出前统一规范化）
     * @return 完整 {@code .xlsx} 文件字节
     */
    public static byte[] exportWorkbook(List<XlsxSheet> sheets) {
        if (sheets == null || sheets.isEmpty()) {
            throw new IllegalArgumentException("多 Sheet 工作簿至少需要一个工作表");
        }
        List<String> names = normalizeSheetNames(sheets);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.setMethod(ZipOutputStream.DEFLATED);
            writeEntry(zos, "[Content_Types].xml", workbookContentTypesXml(sheets.size()));
            writeEntry(zos, "_rels/.rels", rootRelsXml());
            writeEntry(zos, "xl/styles.xml", stylesXml());
            writeEntry(zos, "xl/workbook.xml", multiSheetWorkbookXml(names));
            writeEntry(zos, "xl/_rels/workbook.xml.rels", multiSheetWorkbookRelsXml(sheets.size()));
            for (int i = 0; i < sheets.size(); i++) {
                XlsxSheet sheet = sheets.get(i);
                writeEntry(zos, "xl/worksheets/sheet" + (i + 1) + ".xml",
                        styledSheetXml(sheet.headers(), sheet.rows()));
            }
        } catch (IOException e) {
            // ByteArrayOutputStream 不抛 IOException，防御性保留
            throw new IllegalStateException("XLSX 工作簿生成失败", e);
        }
        return bos.toByteArray();
    }

    /**
     * 规范化工作表名：清洗 Excel 禁用字符（{@code : \ / ? * [ ]}）→ 下划线、按代码点截断 31 字符、
     * 重名追加序号后缀。Excel 对重名工作表直接拒绝打开，此处必须去重。
     */
    private static List<String> normalizeSheetNames(List<XlsxSheet> sheets) {
        List<String> names = new ArrayList<>(sheets.size());
        for (XlsxSheet sheet : sheets) {
            String cleaned = sheet.name().replaceAll("[:\\\\/?*\\[\\]]", "_").trim();
            String truncated = truncate31(cleaned.isEmpty() ? "Sheet" : cleaned);
            String candidate = truncated;
            int seq = 2;
            while (names.contains(candidate)) {
                String suffix = "_" + seq++;
                int keep = Math.min(truncated.length(), 31 - suffix.length());
                candidate = truncated.substring(0, keep) + suffix;
            }
            names.add(candidate);
        }
        return names;
    }

    /** 按代码点截断到 31 字符（Excel 上限），避免把代理对截成半截。 */
    private static String truncate31(String s) {
        if (s.codePointCount(0, s.length()) <= 31) {
            return s;
        }
        int end = 0;
        int count = 0;
        while (end < s.length() && count < 31) {
            int cp = s.codePointAt(end);
            end += Character.charCount(cp);
            count++;
        }
        return s.substring(0, end);
    }

    /** 多 Sheet 版 Content_Types：工作簿 + 样式表 + N 张工作表逐一登记。 */
    private static String workbookContentTypesXml(int sheetCount) {
        StringBuilder sb = new StringBuilder(256 + 128 * sheetCount);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<Types xmlns=\"").append(NS_PKG_REL).append("\">\n")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>\n")
                .append("<Override PartName=\"/xl/workbook.xml\" ")
                .append("ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>\n")
                .append("<Override PartName=\"/xl/styles.xml\" ")
                .append("ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>\n");
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i).append(".xml\" ")
                    .append("ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n");
        }
        sb.append("</Types>");
        return sb.toString();
    }

    /** 多 Sheet 版 workbook.xml：按顺序登记各工作表名。 */
    private static String multiSheetWorkbookXml(List<String> names) {
        StringBuilder sb = new StringBuilder(128 + 64 * names.size());
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<workbook xmlns=\"").append(NS_MAIN).append("\" xmlns:r=\"").append(NS_REL).append("\">\n")
                .append("<sheets>");
        for (int i = 0; i < names.size(); i++) {
            sb.append("<sheet name=\"").append(xmlEscape(names.get(i)))
                    .append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        sb.append("</sheets>\n</workbook>");
        return sb.toString();
    }

    /** 多 Sheet 版工作簿关系：rId1..rIdN → sheet1..sheetN（rId 后缀即文件序号，无独立 styles 关系——样式由 sheet 内 s= 索引）。 */
    private static String multiSheetWorkbookRelsXml(int sheetCount) {
        StringBuilder sb = new StringBuilder(96 + 128 * sheetCount);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<Relationships xmlns=\"").append(NS_PKG_REL).append("\">");
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"").append(NS_REL).append("/worksheet")
                    .append("\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
        }
        sb.append("</Relationships>");
        return sb.toString();
    }

    /**
     * 最小合规 styles.xml：仅两组字体（常规 / 加粗）与三格填充（none / gray125 规范占位 / 表头灰底），
     * cellXfs 下标 1 即表头样式（font=1 + fill=2）。文件由各工作表 {@code s="1"} 单元格引用。
     */
    private static String stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<styleSheet xmlns=\"" + NS_MAIN + "\">\n"
                + "<fonts count=\"2\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "</fonts>\n"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD9E1F2\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>\n"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>\n"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>\n"
                + "<cellXfs count=\"2\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                + "</cellXfs>\n"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>\n"
                + "</styleSheet>";
    }

    /** 带样式的单表 XML：首行（表头）单元格套 {@code s="1"}，并按内容自适应声明列宽。 */
    private static String styledSheetXml(List<String> headers, List<List<String>> rows) {
        int totalCols = (headers == null) ? 0 : headers.size();
        int totalRows = 1 + ((rows == null) ? 0 : rows.size());
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<worksheet xmlns=\"").append(NS_MAIN).append("\">\n");
        if (totalCols > 0) {
            sb.append("<cols>");
            for (int c = 0; c < totalCols; c++) {
                sb.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
                        .append("\" width=\"").append(columnWidth(headers, rows, c))
                        .append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>\n");
        }
        if (totalCols > 0 || totalRows > 0) {
            String lastCol = colLetter(Math.max(0, totalCols - 1));
            sb.append("<dimension ref=\"A1:").append(lastCol).append(totalRows).append("\"/>\n");
        }
        sb.append("<sheetData>\n");
        sb.append(rowXml(0, headers, 1));
        if (rows != null) {
            for (int r = 0; r < rows.size(); r++) {
                sb.append(rowXml(r + 1, rows.get(r), -1));
            }
        }
        sb.append("</sheetData>\n");
        sb.append("</worksheet>");
        return sb.toString();
    }

    /**
     * 估算第 {@code col} 列的显示宽度（Excel 宽度单位 ≈ 半角字符数）：全角 / CJK 按 2 计，
     * 取表头与该列所有单元格的最大值，加 2 边距后钳制到 [6, 50]。
     */
    private static int columnWidth(List<String> headers, List<List<String>> rows, int col) {
        int max = 0;
        if (headers != null && col < headers.size()) {
            max = displayWidth(headers.get(col));
        }
        if (rows != null) {
            for (List<String> row : rows) {
                if (row != null && col < row.size()) {
                    max = Math.max(max, displayWidth(row.get(col)));
                }
            }
        }
        return Math.min(50, Math.max(6, max + 2));
    }

    /** 字符串显示宽度：CJK / 全角字符计 2，其余计 1。 */
    private static int displayWidth(String s) {
        if (s == null) {
            return 0;
        }
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += (s.charAt(i) > 0xFF) ? 2 : 1;
        }
        return w;
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
        sb.append(rowXml(0, headers, -1));
        if (rows != null) {
            for (int r = 0; r < rows.size(); r++) {
                sb.append(rowXml(r + 1, rows.get(r), -1));
            }
        }
        sb.append("</sheetData>\n");
        sb.append("</worksheet>");
        return sb.toString();
    }

    /**
     * 单行 XML。{@code styleId} 为 styles.xml 中 cellXfs 下标（{@code s} 属性），
     * {@code -1} 表示不套样式——单表路径恒传 -1，产物字节与引入样式前完全一致。
     */
    private static String rowXml(int rowIndex0, List<String> cells, int styleId) {
        int excelRow = rowIndex0 + 1;
        StringBuilder sb = new StringBuilder();
        sb.append("<row r=\"").append(excelRow).append("\">");
        if (cells != null) {
            for (int c = 0; c < cells.size(); c++) {
                String ref = colLetter(c) + excelRow;
                sb.append("<c r=\"").append(ref).append("\"");
                if (styleId >= 0) {
                    sb.append(" s=\"").append(styleId).append("\"");
                }
                sb.append(" t=\"inlineStr\">")
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
