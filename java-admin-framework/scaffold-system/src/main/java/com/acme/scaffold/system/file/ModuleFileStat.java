package com.acme.scaffold.system.file;

/**
 * 单来源模块文件统计（按 source_module 分组聚合）。
 *
 * @param sourceModule 来源功能模块（如 workflow / report / file-management）
 * @param fileCount    未删记录数
 * @param totalSize    占用字节数合计
 */
public record ModuleFileStat(
        String sourceModule,
        long fileCount,
        long totalSize) {
}
