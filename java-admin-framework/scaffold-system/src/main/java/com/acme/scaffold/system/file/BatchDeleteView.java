package com.acme.scaffold.system.file;

import java.util.List;

/**
 * 批量删除结果（V50，部分成功语义）：单项失败不影响其余，调用方按成功数/失败明细提示。
 *
 * @param deletedCount 成功删除的记录数
 * @param failures     失败明细（含原因），全部成功时为空列表
 */
public record BatchDeleteView(
        int deletedCount,
        List<BatchDeleteFailure> failures) {
}
