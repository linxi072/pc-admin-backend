package com.acme.scaffold.system.export.application;

/**
 * 异步导出任务快照（E5）。任务状态保存在内存中，重启后需重新提交；
 * 产物字节通过 {@code FileStoragePort} 落盘，状态仅保存存储名，故不随大文件膨胀。
 *
 * @param jobId        任务 ID（UUID 去横线）
 * @param templateCode 导出模板编码
 * @param format       导出格式（CSV/XLSX/PDF）
 * @param status       任务状态
 * @param fileName     产物文件名（不含扩展名）
 * @param storedName   落盘后的存储名（仅成功时非空）
 * @param error        失败原因（仅失败时非空）
 * @param submittedAt  提交时间戳（毫秒）
 * @param finishedAt   完成时间戳（毫秒，未完成为 null）
 */
public record ExportJob(
        String jobId,
        String templateCode,
        String format,
        Status status,
        String fileName,
        String storedName,
        String error,
        long submittedAt,
        Long finishedAt) {

    public enum Status {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED
    }
}
