package com.acme.scaffold.common.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 可复用的 CSV 写出器（零依赖，不引入 POI）。
 *
 * <p>特性：
 * <ul>
 *   <li>UTF-8 BOM 前缀，保证 Excel 正确识别中文；</li>
 *   <li>RFC4180 风格转义：字段含逗号 / 引号 / 换行时整体加双引号，内部双引号转义为两个双引号；</li>
 *   <li>CRLF 行分隔，避免 Excel 在部分平台识别错位。</li>
 * </ul>
 *
 * <p>本类为纯函数工具，不依赖 Spring 容器，便于在任意导出任务与离线单测中复用。
 */
public final class CsvExporter {

    private static final char COMMA = ',';
    private static final char QUOTE = '"';
    private static final String CRLF = "\r\n";

    private CsvExporter() {
    }

    /**
     * 将表头与行数据写出为 CSV（UTF-8 + BOM）。
     *
     * @param out     输出流（由调用方负责关闭）
     * @param headers 表头列
     * @param rows    数据行（每行字段数应与表头一致）
     */
    public static void write(OutputStream out, List<String> headers, List<List<String>> rows) throws IOException {
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        // UTF-8 BOM 前缀：Excel 据此以 UTF-8 而非系统默认编码打开，避免中文乱码
        w.write("﻿");
        writeLine(w, headers);
        if (rows != null) {
            for (List<String> row : rows) {
                writeLine(w, row);
            }
        }
        w.flush();
    }

    private static void writeLine(Writer w, List<String> cells) throws IOException {
        if (cells == null || cells.isEmpty()) {
            w.write(CRLF);
            return;
        }
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                w.write(COMMA);
            }
            w.write(escape(cells.get(i)));
        }
        w.write(CRLF);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        // 先做公式注入中和，再按 RFC4180 加引号——顺序不可颠倒，否则前置的单引号会被包进引号内失效
        String safe = neutralizeFormula(value);
        boolean needQuote = safe.indexOf(COMMA) >= 0
                || safe.indexOf(QUOTE) >= 0
                || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0;
        if (!needQuote) {
            return safe;
        }
        StringBuilder sb = new StringBuilder(safe.length() + 2);
        sb.append(QUOTE);
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            if (c == QUOTE) {
                sb.append(QUOTE);
            }
            sb.append(c);
        }
        sb.append(QUOTE);
        return sb.toString();
    }

    /**
     * CSV 公式注入防护（CWE-1236）：以 {@code = + - @}、Tab、CR 开头的单元格在 Excel / WPS 打开时
     * 会被当作公式执行，导出内容含用户可控字段（用户名、备注等）即构成注入面。前置单引号强制按文本处理。
     *
     * <p>例外：{@code -5}、{@code +3.14} 这类合法数值不中和，避免数值列被整体转成文本。</p>
     */
    private static String neutralizeFormula(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char c = value.charAt(0);
        boolean risky = c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r';
        if (!risky) {
            return value;
        }
        if ((c == '-' || c == '+') && isPlainNumber(value.substring(1))) {
            return value;
        }
        return "'" + value;
    }

    /** 是否为「纯数字」（可含一个小数点），用于区分负数值与以 - 开头的公式。 */
    private static boolean isPlainNumber(String s) {
        if (s.isEmpty()) {
            return false;
        }
        boolean dotSeen = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') {
                continue;
            }
            if (ch == '.' && !dotSeen) {
                dotSeen = true;
                continue;
            }
            return false;
        }
        return true;
    }
}
