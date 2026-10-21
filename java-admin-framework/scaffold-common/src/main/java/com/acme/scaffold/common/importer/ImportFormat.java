package com.acme.scaffold.common.importer;

import java.util.Locale;

/**
 * 导入文件格式枚举（{@link #CSV} / {@link #XLSX}）。
 *
 * <p>与 {@code common.export.ExportFormat} 对称：XLSX 由纯 Java（{@code java.util.zip} + OOXML/XML）
 * 零依赖读写，不引入 POI；CSV 复用 {@code CsvExporter} 写出与 {@link CsvDataReader} 读取。
 * 两种格式共享同一份「表头 + 行」表格模型（{@link TabularData}），因此同一
 * {@link ImportTask} 可同时接受 CSV 与 XLSX 上传。</p>
 *
 * <p>本类是纯函数工具，不依赖 Spring 容器。</p>
 */
public enum ImportFormat {

    CSV("csv", "text/csv; charset=utf-8"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extension;
    private final String contentType;

    ImportFormat(String extension, String contentType) {
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
    public static ImportFormat from(String name) {
        if (name == null) {
            return null;
        }
        try {
            return ImportFormat.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 按上传文件名后缀推断格式（忽略大小写），无法识别返回 {@code null}。
     *
     * <p>刻意<b>不</b>依据 Content-Type 判断：浏览器对 CSV 上报的 MIME 五花八门
     * （{@code application/vnd.ms-excel} / {@code text/plain} / {@code application/octet-stream}），
     * 后缀更可靠。</p>
     *
     * @param fileName 原始文件名（可含路径）
     * @return 推断出的格式；后缀缺失或不受支持时为 {@code null}
     */
    public static ImportFormat detect(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        String ext = fileName.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
        for (ImportFormat f : values()) {
            if (f.extension.equals(ext)) {
                return f;
            }
        }
        return null;
    }
}
