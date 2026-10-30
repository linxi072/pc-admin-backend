package com.acme.scaffold.system.file;

/**
 * 秒传预检结果（V50）：上传前由前端计算内容 SHA-256 回传，服务端告知是否可秒传。
 *
 * @param contentHash        规范化后的内容哈希（64 位小写十六进制）
 * @param exists             是否已存在同内容文件（可秒传）
 * @param fileId             命中记录 ID（exists=false 时为 null）
 * @param originalName       命中记录原始文件名（exists=false 时为 null）
 * @param fileSize           命中记录文件大小（exists=false 时为 null）
 */
public record DedupCheckView(
        String contentHash,
        boolean exists,
        Long fileId,
        String originalName,
        Long fileSize) {
}
