package com.acme.scaffold.common.export;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯 Java 零依赖 PDF 表格导出器（E5）。
 *
 * <p>不引入 iText / PDFBox / OpenPDF，按 PDF 1.4 规范直接拼装对象与交叉引用表，
 * 与 {@link XlsxDataExporter} 同属「零依赖导出工具」家族。支持能力：</p>
 * <ul>
 *   <li>A4 纵向分页，表头每页重复，列宽按内容自适应；</li>
 *   <li>中英混排：ASCII 走 Helvetica（Type1），非 ASCII 走 Adobe-GB1 标准 CJK 字体
 *       {@code STSong-Light} + {@code UniGB-UCS2-H} 编码（UCS-2 BE），
 *       依赖阅读器内置 CJK 字体，无需嵌入字体文件，故产物极小；</li>
 *   <li>单元格内容转义（PDF literal / hex string）与公式注入中性化，避免查看器执行异常行为。</li>
 * </ul>
 *
 * <p>局限：CJK 字形由阅读器提供，极少数不带 CJK 字体包的阅读器可能回退为方框；
 * 对此场景仍可使用 CSV / XLSX。本类只处理文本表格，不处理图片/矢量图形。</p>
 */
public final class PdfExporter {

    private static final String FONT_LATIN = "F1";
    private static final String FONT_CJK = "F2";
    private static final float PAGE_W = 595f;
    private static final float PAGE_H = 842f;
    private static final float MARGIN = 36f;
    private static final float ROW_H = 16f;
    private static final float FONT_SIZE = 9f;
    private static final float CHAR_W = 5.2f;
    private static final int MAX_COL_DISPLAY_WIDTH = 42;
    private static final String UCS2_BOM = "FEFF";

    private PdfExporter() {
    }

    /** 无标题导出。 */
    public static byte[] export(List<String> headers, List<List<String>> rows) {
        return export(headers, rows, null);
    }

    /**
     * 生成完整 PDF 字节。
     *
     * @param headers 表头（列顺序）；为空时产出仅标题的空表
     * @param rows    数据行（单元格允许为 null，按空串处理）
     * @param title   可选标题（首页顶部加粗展示），null/空串则忽略
     */
    public static byte[] export(List<String> headers, List<List<String>> rows, String title) {
        List<String> safeHeaders = headers == null ? List.of() : headers;
        List<List<String>> safeRows = rows == null ? List.of() : rows;
        List<Integer> widths = columnWidths(safeHeaders, safeRows);
        int cols = safeHeaders.size();
        List<List<List<String>>> pages = paginate(safeRows, cols, title != null && !title.isBlank());

        List<byte[]> contents = new ArrayList<>(pages.size());
        for (int p = 0; p < pages.size(); p++) {
            contents.add(pageContent(pages.get(p), safeHeaders, widths, cols, title, p == 0));
        }
        return assemble(contents);
    }

    /** 按内容最大显示宽度计算列宽（CJK 记 2，ASCII 记 1），并缩放到 A4 可用宽度。 */
    private static List<Integer> columnWidths(List<String> headers, List<List<String>> rows) {
        int cols = headers.size();
        int[] max = new int[cols];
        for (int c = 0; c < cols; c++) {
            max[c] = displayWidth(text(headers.get(c)));
        }
        for (List<String> row : rows) {
            for (int c = 0; c < cols; c++) {
                String v = c < row.size() ? text(row.get(c)) : "";
                max[c] = Math.max(max[c], displayWidth(v));
            }
        }
        float available = PAGE_W - 2 * MARGIN;
        long total = 0;
        int[] widths = new int[cols];
        for (int c = 0; c < cols; c++) {
            widths[c] = Math.max(4, Math.min(MAX_COL_DISPLAY_WIDTH, max[c] + 2));
            total += widths[c];
        }
        List<Integer> result = new ArrayList<>(cols);
        for (int c = 0; c < cols; c++) {
            float w = total > available / CHAR_W ? widths[c] * available / (total * CHAR_W) : widths[c] * CHAR_W;
            result.add(Math.max(24, Math.round(w)));
        }
        return result;
    }

    /** 分页：扣掉标题与表头后按可用高度切分数据行。 */
    private static List<List<List<String>>> paginate(List<List<String>> rows, int cols, boolean hasTitle) {
        float usable = PAGE_H - 2 * MARGIN - (hasTitle ? ROW_H * 2 : 0);
        int perPage = Math.max(1, (int) Math.floor(usable / ROW_H) - 1);
        List<List<List<String>>> pages = new ArrayList<>();
        if (rows.isEmpty()) {
            pages.add(List.of());
            return pages;
        }
        for (int from = 0; from < rows.size(); from += perPage) {
            pages.add(new ArrayList<>(rows.subList(from, Math.min(from + perPage, rows.size()))));
        }
        return pages;
    }

    /** 构建单页内容流（表格线 + 文本），单位 pt，坐标原点在左下角。 */
    private static byte[] pageContent(List<List<String>> pageRows, List<String> headers,
                                      List<Integer> widths, int cols, String title, boolean firstPage) {
        StringBuilder content = new StringBuilder();
        float x0 = MARGIN;
        float top = PAGE_H - MARGIN;
        float y = top;

        if (firstPage && title != null && !title.isBlank()) {
            drawMixedText(content, x0, y - FONT_SIZE, text(title));
            y -= ROW_H * 2;
        }

        List<Float> colX = new ArrayList<>(cols + 1);
        colX.add(x0);
        for (int w : widths) {
            colX.add(colX.get(colX.size() - 1) + w);
        }
        float tableRight = colX.get(cols);

        StringBuilder lines = new StringBuilder();
        float headerTop = y;
        float headerBottom = y - ROW_H;
        line(lines, x0, headerTop, tableRight, headerTop);
        line(lines, x0, headerBottom, tableRight, headerBottom);
        for (int c = 0; c <= cols; c++) {
            float x = Math.min(colX.get(c), tableRight);
            line(lines, x, headerTop, x, headerBottom - ROW_H * pageRows.size());
        }

        for (int r = 0; r < pageRows.size(); r++) {
            float rowBottom = headerBottom - ROW_H * (r + 1);
            line(lines, x0, rowBottom, tableRight, rowBottom);
        }

        content.append("0.6 w 0 0 0 RG\n").append(lines).append("S\n");

        for (int c = 0; c < cols; c++) {
            drawMixedText(content, colX.get(c) + 3, headerBottom + 4,
                    text(headers.get(c)));
        }
        for (int r = 0; r < pageRows.size(); r++) {
            List<String> row = pageRows.get(r);
            for (int c = 0; c < cols; c++) {
                String v = c < row.size() ? text(row.get(c)) : "";
                drawMixedText(content, colX.get(c) + 3, headerBottom - ROW_H * (r + 1) + 4, v);
            }
        }
        return content.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void drawMixedText(StringBuilder out, float x, float y, String value) {
        if (value.isEmpty()) {
            return;
        }
        StringBuilder ascii = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 0x20 && ch < 0x7f) {
                cjk.setLength(0);
                ascii.append(ch);
            } else {
                if (ascii.length() > 0) {
                    drawText(out, FONT_LATIN, x, y, literal(ascii));
                    ascii.setLength(0);
                }
                cjk.append(ch);
            }
        }
        if (ascii.length() > 0) {
            drawText(out, FONT_LATIN, x, y, literal(ascii));
        }
        if (cjk.length() > 0) {
            drawText(out, FONT_CJK, x, y, hex(cjk));
        }
    }

    private static void drawText(StringBuilder out, String font, float x, float y, String operand) {
        out.append("BT /").append(font).append(' ').append(fmt(FONT_SIZE)).append(" Tf ")
                .append("1 0 0 1 ").append(fmt(x)).append(' ').append(fmt(y))
                .append(" Tm ").append(operand).append(" Tj ET\n");
    }

    private static void line(StringBuilder out, float x1, float y1, float x2, float y2) {
        out.append(fmt(x1)).append(' ').append(fmt(y1)).append(" m ")
                .append(fmt(x2)).append(' ').append(fmt(y2)).append(" l\n");
    }

    /** PDF literal string 转义；控制字符统一替换为空格，避免查看器解析异常。 */
    private static String literal(StringBuilder value) {
        StringBuilder sb = new StringBuilder(value.length() + 2).append('(');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' || ch == '(' || ch == ')') {
                sb.append('\\').append(ch);
            } else {
                sb.append(ch);
            }
        }
        return sb.append(')').toString();
    }

    /** Adobe-GB1 标准 CJK 字体：UCS-2 BE 十六进制串（带 BOM）。 */
    private static String hex(CharSequence value) {
        StringBuilder sb = new StringBuilder(value.length() * 4 + 2).append('<').append(UCS2_BOM);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isHighSurrogate(ch) && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                int cp = Character.toCodePoint(ch, value.charAt(++i));
                String c = new String(Character.toChars(cp));
                appendUcs2(sb, c.charAt(0));
                if (c.length() > 1) {
                    appendUcs2(sb, c.charAt(1));
                }
            } else {
                appendUcs2(sb, ch);
            }
        }
        return sb.append('>').toString();
    }

    private static void appendUcs2(StringBuilder sb, char ch) {
        sb.append(HEX[(ch >> 12) & 0xF]).append(HEX[(ch >> 8) & 0xF])
                .append(HEX[(ch >> 4) & 0xF]).append(HEX[ch & 0xF]);
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /** 组装 PDF 对象表 + xref + trailer。 */
    private static byte[] assemble(List<byte[]> pageContents) {
        int pageCount = pageContents.size();
        int firstPageObj = 7;
        int objectCount = 7 + pageCount * 2;

        List<byte[]> objects = new ArrayList<>(objectCount);
        objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
        StringBuilder kids = new StringBuilder();
        for (int p = 0; p < pageCount; p++) {
            if (p > 0) {
                kids.append(' ');
            }
            kids.append(firstPageObj + p * 2).append(" 0 R");
        }
        objects.add(bytes("<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>"));
        objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));
        objects.add(bytes("<< /Type /Font /Subtype /Type0 /BaseFont /STSong-Light /Encoding /UniGB-UCS2-H "
                + "/DescendantFonts [5 0 R] >>"));
        objects.add(bytes("<< /Type /Font /Subtype /CIDFontType0 /BaseFont /STSong-Light /CIDSystemInfo "
                + "<< /Registry (Adobe) /Ordering (GB1) /Supplement 4 >> /FontDescriptor 6 0 R /DW 1000 >>"));
        objects.add(bytes("<< /Type /FontDescriptor /FontName /STSong-Light /Flags 6 "
                + "/FontBBox [-250 -250 1000 1000] /ItalicAngle 0 /Ascent 880 /Descent -120 /CapHeight 880 /StemV 78 >>"));

        for (int p = 0; p < pageCount; p++) {
            int pageObj = firstPageObj + p * 2;
            int contentObj = pageObj + 1;
            objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + fmt(PAGE_W) + ' ' + fmt(PAGE_H)
                    + "] /Resources << /Font << /" + FONT_LATIN + " 3 0 R /" + FONT_CJK + " 4 0 R >> >> "
                    + "/Contents " + contentObj + " 0 R >>"));
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            stream.writeBytes(bytes("<< /Length " + pageContents.get(p).length + " >>\nstream\n"));
            stream.writeBytes(pageContents.get(p));
            stream.writeBytes(bytes("\nendstream"));
            objects.add(stream.toByteArray());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(bytes("%PDF-1.4\n%"));
        out.writeBytes(new byte[]{(byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'});
        List<Integer> offsets = new ArrayList<>(objectCount);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            out.writeBytes(bytes((i + 1) + " 0 obj\n"));
            out.writeBytes(objects.get(i));
            out.writeBytes(bytes("\nendobj\n"));
        }
        int xrefOffset = out.size();
        StringBuilder xref = new StringBuilder("xref\n0 ").append(objectCount + 1).append("\n")
                .append("0000000000 65535 f \n");
        for (int offset : offsets) {
            xref.append(String.format("%010d 00000 n \n", offset));
        }
        xref.append("trailer\n<< /Size ").append(objectCount + 1)
                .append(" /Root 1 0 R >>\nstartxref\n").append(xrefOffset).append("\n%%EOF\n");
        out.writeBytes(bytes(xref.toString()));
        return out.toByteArray();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            w += ch < 0x80 ? 1 : 2;
        }
        return w;
    }

    private static String text(String value) {
        if (value == null) {
            return "";
        }
        return CsvExporter.neutralizeFormula(value);
    }

}
