package com.acme.scaffold.common.importer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单行导入失败的完整明细：除行号与失败原因外，还保留该行的<b>原始单元格值</b>，
 * 供「导入失败报告（CSV）」还原行内容——用户可离线修复后重传，无需回源文件对照行号。
 *
 * @param segment 所属段名（工作表名；单段导入为模板展示名，便于多段导入时区分失败行来源）
 * @param rowNo   数据行号（从 1 开始，不含表头；与 {@link RowError#rowNo()} 同口径）
 * @param message 失败原因（多字段校验失败时以「；」连接）
 * @param values  该行原始值（表头名 → 单元格文本，保持模板表头插入序）
 */
public record FailedRow(String segment, int rowNo, String message, Map<String, String> values) {

    public FailedRow {
        segment = segment == null ? "" : segment;
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** 兼容旧构造（未携带段名）。 */
    public FailedRow(int rowNo, String message, Map<String, String> values) {
        this("", rowNo, message, values);
    }
}
