package com.acme.scaffold.system.file;

/**
 * 批量删除失败明细（V50）：部分成功语义下逐项反馈失败原因。
 *
 * @param id     失败的文件记录 ID
 * @param reason 失败原因（如「文件不存在或已删除」）
 */
public record BatchDeleteFailure(
        Long id,
        String reason) {
}
