package com.acme.scaffold.common.importer;

import java.util.List;

/**
 * 命名单表段：多段结构导入的基本单元（与导出侧 {@code XlsxSheet} 对称）。
 *
 * <p>一个段对应工作簿中的一张工作表（或 CSV 上下文中的整体单表），由段名 + 一份
 * {@link TabularData}（表头 + 数据行）构成。读取器（{@link XlsxDataReader#readWorkbook}）
 * 按工作表顺序产出 {@code List<Segment>}，导入中心再按段名将其与各 {@code SegmentSpec} 匹配。</p>
 *
 * <p>本记录为不可变纯数据，不依赖 Spring 容器。</p>
 *
 * @param name 段名（工作表名；匹配时忽略大小写与首尾空白）
 * @param data 该段的表格化数据（表头 + 数据行，均与 {@link TabularData} 不变式一致）
 */
public record Segment(String name, TabularData data) {

    public Segment {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("段名不能为空");
        }
        data = data == null ? new TabularData(List.of(), List.of()) : data;
    }

    /** 数据行数（不含表头）。 */
    public int rowCount() {
        return data.rowCount();
    }

    /** 是否无任何内容（既无表头也无数据行）。 */
    public boolean isEmpty() {
        return data.isEmpty();
    }
}
