package com.acme.scaffold.common.importer;

import java.util.List;

/**
 * 表格化中间模型：导入解析的统一产物，屏蔽 CSV 与 XLSX 的格式差异。
 *
 * <p>不变式（由各读取器保证，下游无需再做越界判断）：
 * <ul>
 *   <li>{@code headers} 与 {@code rows} 中的单元格均<b>非 null</b>（空白以空串表示）；</li>
 *   <li>所有行（含表头）均已补齐至同一列宽——缺失单元格补空串，
 *       因此 {@code rows} 中任意一行都可安全按下标访问到 {@code headers.size()} 列。</li>
 * </ul>
 *
 * <p>本记录为不可变纯数据，不依赖 Spring 容器。</p>
 *
 * @param headers 首行表头（可为空列表）
 * @param rows    数据行（不含表头；可为空列表）
 */
public record TabularData(List<String> headers, List<List<String>> rows) {

    public TabularData {
        headers = headers == null ? List.of() : List.copyOf(headers);
        rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
    }

    /** 数据行数（不含表头）。 */
    public int rowCount() {
        return rows.size();
    }

    /** 表头列数。 */
    public int columnCount() {
        return headers.size();
    }

    /** 是否无任何内容（既无表头也无数据行）。 */
    public boolean isEmpty() {
        return headers.isEmpty() && rows.isEmpty();
    }
}
