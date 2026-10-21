package com.acme.scaffold.common.importer;

import java.util.List;

/**
 * 导入的完整产物：{@link ImportResult}（统计 + 失败明细，随 JSON 返回）
 * 与 {@link FailedRow} 列表（含原始行值，供「导入失败报告 CSV」还原行内容）。
 *
 * <p>常规导入走 {@link ImportResult} 即可；仅当调用方显式要求失败报告时才需要
 * 关心 {@code failedRows}（其条目范围与 {@code result.errors()} 一致，
 * 同受 {@code app.import.max-errors} 上限约束）。</p>
 *
 * @param result     导入/预检结果汇总
 * @param failedRows 失败行完整明细（与 {@code result.errors()} 同序同范围）
 */
public record ImportOutcome(ImportResult result, List<FailedRow> failedRows) {

    public ImportOutcome {
        failedRows = failedRows == null ? List.of() : List.copyOf(failedRows);
    }
}
