package com.acme.scaffold.workbench.report.dto;

import java.util.List;
import java.util.Map;

/**
 * 明细表（列驱动的通用表格数据，供前端动态渲染 el-table）。
 *
 * <p>「最近执行明细 / 异常登录记录 / Top 接口」等异构明细统一收敛为该结构：
 * {@code columns} 定义表头与取值键，{@code rows} 每行是一个列名→展示值的映射
 * （值统一转为字符串，时间格式化为 {@code yyyy-MM-dd HH:mm:ss}，避免前端二次解析）。</p>
 *
 * @param columns 列定义（顺序即展示顺序）
 * @param rows    数据行（键与 {@link Column#key()} 对应）
 */
public record ReportDetailTableVO(List<Column> columns, List<Map<String, String>> rows) {

    /**
     * 列定义。
     *
     * @param key   取值键（与 rows 中 Map 的键一致）
     * @param label 表头中文
     */
    public record Column(String key, String label) {
    }
}
