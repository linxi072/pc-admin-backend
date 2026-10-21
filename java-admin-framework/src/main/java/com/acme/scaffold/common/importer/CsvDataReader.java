package com.acme.scaffold.common.importer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 零依赖 CSV 读取器（RFC4180），与 {@code common.export.CsvExporter} 对称。
 *
 * <p>特性：
 * <ul>
 *   <li>自动剥离 UTF-8 BOM（本框架导出文件带 BOM，往返导入不应把 BOM 当作首列表头的一部分）；</li>
 *   <li>完整支持 RFC4180 引号语义：字段可含逗号 / 换行，字段内双引号以两个双引号转义；</li>
 *   <li>行分隔兼容 {@code CRLF} / {@code LF} / 单独 {@code CR}（Excel 在部分平台另存为 CSV 时使用 CR）；</li>
 *   <li>结果补齐至统一列宽（缺失单元格补空串），保证下游可按下标安全访问；</li>
 *   <li>忽略文件末尾的空白行（用户编辑后常见的多余空行）。</li>
 * </ul>
 *
 * <p>本类是纯函数工具，不依赖 Spring 容器，便于离线单测。</p>
 */
public final class CsvDataReader {

    private static final char BOM = '\uFEFF';

    private CsvDataReader() {
    }

    /**
     * 解析 CSV 字节为首行表头 + 其余数据行。
     *
     * @param bytes CSV 文件字节（UTF-8）
     * @return 表格化数据；输入为 {@code null} / 空时返回空表（不抛异常，交由调用方判定「无数据」）
     */
    public static TabularData read(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new TabularData(List.of(), List.of());
        }
        String text = stripBom(new String(bytes, StandardCharsets.UTF_8));
        List<List<String>> all = parse(text);
        if (all.isEmpty()) {
            return new TabularData(List.of(), List.of());
        }
        return toTabular(all);
    }

    /** 剥离首部 BOM（若存在）。 */
    private static String stripBom(String text) {
        return (!text.isEmpty() && text.charAt(0) == BOM) ? text.substring(1) : text;
    }

    /**
     * RFC4180 状态机解析：逐字符扫描，用 {@code inQuotes} 区分「引号内的字面量」与「结构字符」。
     */
    private static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        // 当前行是否「有内容」：用于区分「真实的空行」与「文件末尾换行符产生的伪行」
        boolean pending = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    // 双引号转义：两个连续双引号代表一个字面双引号
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                cell.append(c);
                i++;
                continue;
            }
            switch (c) {
                case '"' -> {
                    inQuotes = true;
                    pending = true;
                    i++;
                }
                case ',' -> {
                    row.add(cell.toString());
                    cell.setLength(0);
                    pending = true;
                    i++;
                }
                case '\r', '\n' -> {
                    row.add(cell.toString());
                    cell.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                    pending = false;
                    // CRLF 视为单个换行
                    i += (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') ? 2 : 1;
                }
                default -> {
                    cell.append(c);
                    pending = true;
                    i++;
                }
            }
        }
        // 收尾：最后一行未以换行符结束（若无任何待定内容则是末尾换行产生的伪行，丢弃）
        if (pending) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    /** 补齐至统一列宽，并拆出表头与数据行。 */
    private static TabularData toTabular(List<List<String>> raw) {
        int width = raw.stream().mapToInt(List::size).max().orElse(0);
        List<List<String>> padded = new ArrayList<>(raw.size());
        for (List<String> r : raw) {
            List<String> fixed = new ArrayList<>(width);
            for (int c = 0; c < width; c++) {
                fixed.add(c < r.size() ? nullToEmpty(r.get(c)) : "");
            }
            padded.add(fixed);
        }
        return new TabularData(padded.get(0), padded.subList(1, padded.size()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
