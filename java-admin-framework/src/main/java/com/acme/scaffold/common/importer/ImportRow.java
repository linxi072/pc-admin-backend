package com.acme.scaffold.common.importer;

import java.util.Map;

/**
 * 一行待导入数据：以「表头名 → 单元格值」映射暴露，屏蔽 CSV 与 XLSX 的列序差异，
 * 并让 {@link ImportTask} 实现类无需关心文件格式与列下标。
 *
 * <p>值一律为字符串（非 null，缺失列为空串），由 {@link ImportTask} 负责按业务类型解析并校验。</p>
 *
 * @param rowNo  数据行号（从 1 开始计数，<b>不含表头</b>；用于错误定位，
 *               与用户在 Excel/CSV 中看到的物理行号相差 1）
 * @param values 表头名到单元格值的映射
 */
public record ImportRow(int rowNo, Map<String, String> values) {

    public ImportRow {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    /** 取列值；缺失或为 null 时返回空串，调用方无需判 null。 */
    public String value(String header) {
        String v = values.get(header);
        return v == null ? "" : v;
    }

    /** 取列值并去除首尾空白（用于编码 / 状态等不应含空白的字段）。 */
    public String trimmed(String header) {
        return value(header).trim();
    }

    /** 列值是否为空白（缺失 / 空串 / 纯空白）。 */
    public boolean isBlank(String header) {
        return value(header).isBlank();
    }
}
