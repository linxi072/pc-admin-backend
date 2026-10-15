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
        boolean needQuote = value.indexOf(COMMA) >= 0
                || value.indexOf(QUOTE) >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needQuote) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append(QUOTE);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == QUOTE) {
                sb.append(QUOTE);
            }
            sb.append(c);
        }
        sb.append(QUOTE);
        return sb.toString();
    }
}
