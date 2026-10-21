package com.acme.scaffold.common.importer;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 零依赖 XLSX 读取器，与 {@code common.export.XlsxDataExporter} 严格对称。
 *
 * <p>XLSX 本质是 OOXML 规范定义的 ZIP 包（内含若干 XML）。本类仅用 JDK 内置
 * {@link ZipInputStream} + StAX（{@code javax.xml.stream}）解析，无需引入 POI，
 * 适合离线构建环境；与写出器构成「导出→编辑→导入」闭环。</p>
 *
 * <p>解析范围（覆盖后台导入场景）：
 * <ul>
 *   <li>取<b>第一个工作表</b>（优先 {@code xl/worksheets/sheet1.xml}，否则按名称序取首个 worksheet）；</li>
 *   <li>支持共享字符串表（{@code xl/sharedStrings.xml}，{@code t="s"}）与内联字符串
 *       （{@code t="inlineStr"}，即本框架 {@code XlsxDataExporter} 的写出形式）；
 *       其余类型（数值 / 布尔 / 公式结果）统一取其缓存值文本；</li>
 *   <li>依据单元格 {@code r} 属性（如 {@code B3}）定位列号，因此<b>空单元格不会导致列错位</b>
 *       ——这是比「按出现顺序排列」更可靠的做法（Excel 会跳过空单元格不写 {@code <c>}）；</li>
 *   <li>安全加固：禁用 DTD 与外部实体，防 XXE；解析失败统一抛
 *       {@link IllegalArgumentException}，由上层转为 400 业务错误。</li>
 * </ul>
 *
 * <p>实现上一次性读入 ZIP 各条目到内存（上限由上传文件大小约束），换取「与条目顺序无关」的稳健解析
 * ——若改为单遍流式读取，一旦 {@code sharedStrings.xml} 排在工作表之后，字符串索引将无法解析。</p>
 *
 * <p>本类是纯函数工具，不依赖 Spring 容器，便于离线单测。</p>
 */
public final class XlsxDataReader {

    private static final String SHEET1 = "xl/worksheets/sheet1.xml";
    private static final String SHEET_PREFIX = "xl/worksheets/";
    private static final String SHARED_STRINGS = "xl/sharedStrings.xml";

    private XlsxDataReader() {
    }

    /**
     * 解析 XLSX 字节为首行表头 + 其余数据行。
     *
     * @param bytes {@code .xlsx} 文件字节
     * @return 表格化数据；输入为 {@code null} / 空时返回空表
     * @throws IllegalArgumentException 非合法 ZIP / XML 结构（非 OOXML 文件）
     */
    public static TabularData read(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new TabularData(List.of(), List.of());
        }
        Map<String, byte[]> parts = unzip(bytes);
        String sheetName = firstSheetName(parts);
        if (sheetName == null) {
            throw new IllegalArgumentException("不是有效的 XLSX 文件：未找到工作表");
        }
        List<String> shared = parseSharedStrings(parts.get(SHARED_STRINGS));
        List<List<String>> rows = parseSheet(parts.get(sheetName), shared);
        if (rows.isEmpty()) {
            return new TabularData(List.of(), List.of());
        }
        int width = rows.stream().mapToInt(List::size).max().orElse(0);
        List<List<String>> padded = new ArrayList<>(rows.size());
        for (List<String> r : rows) {
            List<String> fixed = new ArrayList<>(width);
            for (int c = 0; c < width; c++) {
                fixed.add(c < r.size() ? r.get(c) : "");
            }
            padded.add(fixed);
        }
        return new TabularData(padded.get(0), padded.subList(1, padded.size()));
    }

    /** 解包全部非目录条目（名称 → 内容）。 */
    private static Map<String, byte[]> unzip(byte[] bytes) {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    parts.put(e.getName(), zis.readAllBytes());
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("不是有效的 XLSX 文件：ZIP 结构损坏", e);
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("不是有效的 XLSX 文件：内容为空");
        }
        return parts;
    }

    /** 取第一个工作表条目名；无工作表返回 {@code null}。 */
    private static String firstSheetName(Map<String, byte[]> parts) {
        if (parts.containsKey(SHEET1)) {
            return SHEET1;
        }
        return parts.keySet().stream()
                .filter(n -> n.startsWith(SHEET_PREFIX) && n.endsWith(".xml") && !n.contains("_rels"))
                .sorted()
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析共享字符串表：每个 {@code <si>} 为一个条目，其内全部 {@code <t>} 文本按序拼接
     * （富文本会拆成多个 {@code <t>}，如 {@code <si><r><t>a</t></r><r><t>b</t></r></si>} → {@code ab}）。
     */
    private static List<String> parseSharedStrings(byte[] xml) {
        List<String> out = new ArrayList<>();
        if (xml == null) {
            return out;
        }
        XMLStreamReader r = newReader(xml);
        try {
            StringBuilder current = null;
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = r.getLocalName();
                    if ("si".equals(name)) {
                        current = new StringBuilder();
                    } else if ("t".equals(name) && current != null) {
                        current.append(r.getElementText());
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && "si".equals(r.getLocalName())) {
                    out.add(current == null ? "" : current.toString());
                    current = null;
                }
            }
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("XLSX 共享字符串表解析失败", e);
        } finally {
            close(r);
        }
        return out;
    }

    /**
     * 解析工作表：按 {@code <row>} → {@code <c>} 逐层读取，单元格值取自 {@code <v>}（数值 / 共享字符串索引）
     * 或 {@code <is><t>}（内联字符串）。
     */
    private static List<List<String>> parseSheet(byte[] xml, List<String> shared) {
        List<List<String>> rows = new ArrayList<>();
        XMLStreamReader r = newReader(xml);
        try {
            List<String> currentRow = null;
            String cellType = null;
            String cellValue = null;
            StringBuilder buffer = null;
            int seqCol = 0;
            int col = -1;
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "row" -> {
                            currentRow = new ArrayList<>();
                            seqCol = 0;
                        }
                        case "c" -> {
                            cellType = attr(r, "t");
                            col = columnIndex(attr(r, "r"), seqCol);
                            seqCol = col + 1;
                            cellValue = null;
                        }
                        case "v", "t" -> buffer = new StringBuilder();
                        default -> {
                            // 其余元素（sheetData / is / r 等）无需处理，值文本由 v / t 承载
                        }
                    }
                } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                    if (buffer != null) {
                        buffer.append(r.getText());
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = r.getLocalName();
                    if (("v".equals(name) || "t".equals(name)) && buffer != null) {
                        cellValue = resolveValue(name, cellType, buffer.toString(), shared);
                        buffer = null;
                    } else if ("c".equals(name) && currentRow != null) {
                        putCell(currentRow, col, cellValue == null ? "" : cellValue);
                    } else if ("row".equals(name) && currentRow != null) {
                        rows.add(currentRow);
                        currentRow = null;
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("XLSX 工作表解析失败", e);
        } finally {
            close(r);
        }
        return rows;
    }

    /**
     * 单元格值归一：
     * <ul>
     *   <li>{@code <t>}（内联字符串）→ 字面文本；</li>
     *   <li>{@code <v>} 且 {@code t="s"} → 共享字符串表下标；</li>
     *   <li>{@code <v>} 其余（数值 / 布尔 / 字符串 / 公式缓存值）→ 字面文本。</li>
     * </ul>
     */
    private static String resolveValue(String elementName, String cellType, String raw, List<String> shared) {
        if ("t".equals(elementName)) {
            return raw;
        }
        if ("s".equals(cellType)) {
            int index = parseIntOr(raw.strip(), -1);
            return (index >= 0 && index < shared.size()) ? shared.get(index) : raw;
        }
        return raw;
    }

    private static void putCell(List<String> row, int col, String value) {
        int target = Math.max(col, 0);
        while (row.size() <= target) {
            row.add("");
        }
        row.set(target, value == null ? "" : value);
    }

    /**
     * 由单元格引用（如 {@code B3} → 1）计算 0 基列号；无引用时回退为顺序列号。
     */
    private static int columnIndex(String ref, int fallback) {
        if (ref == null || ref.isEmpty()) {
            return Math.max(fallback, 0);
        }
        int col = 0;
        boolean any = false;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                col = col * 26 + (c - 'A' + 1);
                any = true;
            } else if (c >= 'a' && c <= 'z') {
                col = col * 26 + (c - 'a' + 1);
                any = true;
            } else {
                break;
            }
        }
        return any ? col - 1 : Math.max(fallback, 0);
    }

    private static String attr(XMLStreamReader r, String name) {
        for (int i = 0; i < r.getAttributeCount(); i++) {
            if (name.equals(r.getAttributeLocalName(i))) {
                return r.getAttributeValue(i);
            }
        }
        return null;
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 创建安全加固的 StAX 读取器（禁用 DTD 与外部实体，防 XXE）。 */
    private static XMLStreamReader newReader(byte[] xml) {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        setPropertyQuietly(factory, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        setPropertyQuietly(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        setPropertyQuietly(factory, XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        try {
            return factory.createXMLStreamReader(
                    new ByteArrayInputStream(xml), StandardCharsets.UTF_8.name());
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("XLSX XML 解析初始化失败", e);
        }
    }

    private static void setPropertyQuietly(XMLInputFactory factory, String name, Object value) {
        try {
            factory.setProperty(name, value);
        } catch (IllegalArgumentException e) {
            // 个别 StAX 实现不支持该属性：忽略，不影响正确性（仅安全加固等级略降）
        }
    }

    private static void close(XMLStreamReader r) {
        try {
            r.close();
        } catch (XMLStreamException e) {
            // 关闭失败无需上抛
        }
    }
}
