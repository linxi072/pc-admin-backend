package com.acme.scaffold.common.importer;

import java.util.List;

/**
 * 多段结构导入任务契约：一个模板对应一个工作簿（多张工作表），每个工作表映射到一个 {@link SegmentSpec}。
 *
 * <p>与导出侧 {@code ExportTask.xlsxSheets()}（{@code XlsxSheet} 列表）形成对称闭环：
 * 导出多段报表用 {@code XlsxDataExporter.exportWorkbook}，导入多段数据用本接口的
 * {@link #segments()} 声明各段，二者以「工作表名」为锚点对齐，支持「导出→编辑→原样回导」。</p>
 *
 * <p>既有的单段 {@link ImportTask} 仍完全有效，无需改动；仅当业务天然含多张关联表
 * （如字典类型 + 字典数据）时，才需实现本接口替代单段契约。导入中心按
 * {@code instanceof MultiSegmentImportTask} 自动识别并走多段编排。</p>
 *
 * <p>约束：多段导入仅支持 XLSX（多工作表是 XLSX 原生能力）；CSV 因无工作表概念，对多段模板不予受理。</p>
 */
public interface MultiSegmentImportTask extends ImportTask {

    /** 多段定义（至少一段，顺序即期望的工作表顺序）。 */
    List<SegmentSpec> segments();

    /**
     * 单段契约 {@code headers()} 在多段下取首段表头，供后端 {@code /templates} 与旧前端兼容
     * （逐段表头已通过 {@link #segments()} 暴露）。
     */
    @Override
    default List<String> headers() {
        return segments().isEmpty() ? List.of() : segments().get(0).headers();
    }

    /**
     * 多段任务不通过单段 {@code validate} 入口校验（应逐段走 {@link SegmentSpec#validate}），
     * 调用即报错，避免被误用为单段模板。
     */
    @Override
    default List<String> validate(ImportRow row) {
        throw new UnsupportedOperationException("多段导入任务不支持单段 validate，请按段调用 SegmentSpec.validate");
    }

    /**
     * 同 {@link #validate(ImportRow)}：多段任务不通过单段 {@code importRow} 落库（应逐段走
     * {@link SegmentSpec#importRow}）。
     */
    @Override
    default void importRow(ImportRow row) {
        throw new UnsupportedOperationException("多段导入任务不支持单段 importRow，请按段调用 SegmentSpec.importRow");
    }
}
