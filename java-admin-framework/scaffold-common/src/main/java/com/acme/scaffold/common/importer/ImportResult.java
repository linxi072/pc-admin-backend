package com.acme.scaffold.common.importer;

import java.util.List;

/**
 * 导入结果汇总：逐行处理的统计与失败明细。
 *
 * <p>导入采用「逐行独立」语义：单行失败不影响其余行（每行由业务服务的自身事务保证一致性），
 * 失败明细回传给前端供用户修正后重传。{@code dryRun=true} 时表示<b>仅预检</b>——
 * 已完成表头校验与逐行业务校验，但未落库。</p>
 *
 * <p>{@code failureCount} 与 {@code errors} 刻意分离：失败行数始终是<b>真实值</b>，
 * 而 {@code errors} 明细受 {@code app.import.max-errors} 上限约束（防大文件产生超大响应体），
 * 因此 {@code errors.size()} 可能小于 {@code failureCount}。</p>
 *
 * @param dryRun       是否为预检模式（true 表示未落库）
 * @param totalRows    实际处理的数据行数（已跳过全空行，不含表头）
 * @param successCount 成功行数（预检模式为「校验通过」的行数）
 * @param failureCount 失败行数（真实值，不受错误明细上限影响）
 * @param errors       失败明细（最多 {@code app.import.max-errors} 条）
 */
public record ImportResult(boolean dryRun, int totalRows, int successCount, int failureCount,
                           List<RowError> errors) {

    public ImportResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** 是否全部行均成功。 */
    public boolean allSucceeded() {
        return failureCount == 0;
    }
}
