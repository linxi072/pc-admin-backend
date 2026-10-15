package com.acme.scaffold.common.export;

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
}
