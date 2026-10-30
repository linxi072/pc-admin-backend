package com.acme.scaffold.common.export;

import java.util.List;

/**
 * XLSX 单工作表数据模型（多 Sheet 工作簿写出用，见 {@link XlsxDataExporter#exportWorkbook}）。
 *
 * <p>不可变值对象：构造时对字段做防御性拷贝与判空校验，工作表名的合法性
 * （非法字符清洗 / 31 字符截断 / 重名去重）由写出器统一处理，调用方无需预处理。</p>
 *
 * @param name    工作表名（展示名，如「汇总」「趋势」）
 * @param headers 表头列（首行，写出时套用加粗 + 灰底样式）
 * @param rows    数据行（每行字段数建议与表头一致；{@code null} 归一为空列表）
 */
public record XlsxSheet(String name, List<String> headers, List<List<String>> rows) {

    public XlsxSheet {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工作表名不能为空");
        }
        headers = (headers == null) ? List.of() : List.copyOf(headers);
        rows = (rows == null) ? List.of() : rows.stream()
                .map(r -> (r == null) ? List.<String>of() : List.copyOf(r))
                .toList();
    }
}
