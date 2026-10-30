package com.acme.scaffold.common.importer;

import java.util.List;

/**
 * 多段导入中单个「段」的契约：独立承担一段数据的表头声明、纯函数校验与落库。
 *
 * <p>与 {@link ImportTask} 的方法签名同名同义，只是粒度细化到段：一个
 * {@link MultiSegmentImportTask} 由若干 {@code SegmentSpec} 组成，每个段对应工作簿中的一张工作表。
 * 段之间互不感知，导入中心逐段独立校验、独立落库、独立统计，单段失败不影响其余段。</p>
 */
public interface SegmentSpec {

    /** 段名（工作表名，用于与上传文件中各工作表按名匹配，忽略大小写与首尾空白）。 */
    String name();

    /** 该段表头（列名，顺序即模板列序）。 */
    List<String> headers();

    /**
     * 校验单行数据（纯函数，不得产生副作用、不得访问数据库以外的可变状态）。
     *
     * @param row 待校验行
     * @return 该行错误信息列表；<b>空列表表示校验通过</b>
     */
    List<String> validate(ImportRow row);

    /**
     * 导入单行数据（落库）。仅当 {@link #validate(ImportRow)} 返回空列表时被调用。
     *
     * @param row 已通过校验的行
     */
    void importRow(ImportRow row);
}
