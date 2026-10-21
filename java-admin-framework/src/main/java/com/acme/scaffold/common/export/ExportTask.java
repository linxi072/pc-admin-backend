package com.acme.scaffold.common.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 导出任务契约：一个可被「导出中心」统一调度的数据导出模板。
 *
 * <p>各业务模块实现该接口并注册为 Spring Bean，即可在导出中心页面中被自动发现与触发；
 * 后端无需为每种数据手写独立接口，新增导出只需新增一个 {@code ExportTask} 实现。
 */
public interface ExportTask {

    /** 模板唯一编码（用于接口路由，如 files）。 */
    String code();

    /** 展示名（前端列表展示）。 */
    String displayName();

    /** 下载文件名（不含 .csv 后缀，导出时自动追加）。 */
    String fileName();

    /** CSV 表头。 */
    List<String> headers();

    /**
     * 根据筛选参数构建 CSV 行数据。
     *
     * @param params 前端透传的筛选参数（如 sourceModule / uploaderName），实现类按需取值
     * @return 行数据（每行字段数需与 {@link #headers()} 一致）
     */
    List<List<String>> rows(Map<String, String> params);

    /**
     * 生成完整 CSV 字节（UTF-8 BOM + RFC4180 转义，经 {@link CsvExporter}）。
     *
     * <p>默认实现为「{@link #headers()} 作表头 + {@link #rows(Map)} 作数据行」的单表结构，
     * 覆盖绝大多数导出场景；多段结构（如报表导出的标题段 + 多个统计段）可覆写本方法自产字节，
     * 此时 {@link #headers()} / {@link #rows(Map)} 不再被调用（实现可返回空列表）。</p>
     *
     * @param params 前端透传的筛选参数（与 {@link #rows(Map)} 同源）
     * @return CSV 字节内容
     */
    default byte[] csv(Map<String, String> params) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            CsvExporter.write(bos, headers(), rows(params));
        } catch (IOException e) {
            // ByteArrayOutputStream 不抛 IOException，防御性保留
            throw new IllegalStateException("CSV 生成失败", e);
        }
        return bos.toByteArray();
    }

    /**
     * XLSX 多 Sheet 导出（多段结构模板的可选能力）。
     *
     * <p>默认返回空列表（模板不支持多 Sheet）。多段结构模板（{@link #headers()} 为空）覆写本方法后
     * 即解锁 XLSX 格式：各段落映射为一个工作表（如报表的汇总 / 分布 / 明细 / 趋势各占一页），
     * 经 {@link XlsxDataExporter#exportWorkbook} 写出（表头加粗灰底 + 列宽自适应）；
     * 单表结构模板无需覆写，走「headers + rows 单 Sheet」路径。</p>
     *
     * @param params 前端透传的筛选参数（与 {@link #rows(Map)} 同源）
     * @return 工作表列表；空列表表示不支持多 Sheet 导出
     */
    default List<XlsxSheet> xlsxSheets(Map<String, String> params) {
        return List.of();
    }

    /**
     * 是否支持 XLSX 格式（决定 {@code /templates} 返回的 {@code formats} 与前端按钮显隐）。
     *
     * <p>默认「单表结构（headers 非空）即支持」；多段结构模板在提供
     * {@link #xlsxSheets(Map)} 后覆写为 {@code true}。</p>
     */
    default boolean supportsXlsx() {
        return !headers().isEmpty();
    }
}
