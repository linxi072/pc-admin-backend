package com.acme.scaffold.common.importer;

import java.util.List;

/**
 * 导入任务契约：一个可被「导入中心」统一调度的数据导入模板，与 {@code common.export.ExportTask} 对称。
 *
 * <p>各业务模块实现该接口并注册为 Spring Bean，即可在导入中心页面中被自动发现与触发；
 * 后端无需为每种数据手写独立上传接口，新增导入只需新增一个实现。</p>
 *
 * <p><b>校验与落库分离</b>（刻意设计）：
 * <ul>
 *   <li>{@link #validate(ImportRow)} 为<b>纯函数</b>校验，不产生任何副作用；</li>
 *   <li>导入中心在正式导入时先调用 {@code validate}，无错误才调用 {@link #importRow(ImportRow)}；</li>
 *   <li>「预检」模式（dryRun）只调用 {@code validate}，让用户先确认数据无误再真正落库；
 *       预检<b>不</b>校验数据库层面的唯一性冲突（如重复值），此类冲突在正式导入时由业务服务返回
 *       并计入该行失败明细。</li>
 * </ul>
 */
public interface ImportTask {

    /** 模板唯一编码（用于接口路由，如 dict-data）。 */
    String code();

    /** 展示名（前端列表展示）。 */
    String displayName();

    /**
     * 模板表头（列名，顺序即模板列序）。
     *
     * <p>用于两处：生成下载模板（仅表头行）与校验上传文件的表头是否与模板一致。</p>
     */
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
     * <p>实现应调用既有业务服务（而非直接写库），以复用其事务、缓存驱逐与业务规则；
     * 抛出异常表示该行导入失败，导入中心会捕获并计入该行错误明细（不影响其余行）。</p>
     *
     * @param row 已通过校验的行
     */
    void importRow(ImportRow row);
}
