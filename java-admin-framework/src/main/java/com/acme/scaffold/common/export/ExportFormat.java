package com.acme.scaffold.common.export;

import java.util.Locale;

/**
 * 导出格式枚举。
 *
 * <p>XLSX 采用纯 Java（{@code java.util.zip} + OOXML/XML）零依赖写出，
 * 不引入 POI；CSV 复用 {@link CsvExporter}。两种格式共享同一份
 * {@code (headers, rows)} 表头/行数据模型，因此同一 {@link ExportTask} 可一键双格式导出。</p>
 */
public enum ExportFormat {

    CSV("csv", "text/csv; charset=utf-8"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String contentType;

    ExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /**
     * 按名称（忽略大小写、去空格）解析，非法值返回 {@code null}，由调用方决定默认或报错。
     */
    public static ExportFormat from(String name) {
        if (name == null) {
            return null;
        }
        try {
            return ExportFormat.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
