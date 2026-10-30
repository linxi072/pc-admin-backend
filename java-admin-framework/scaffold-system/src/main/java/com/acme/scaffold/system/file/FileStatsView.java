package com.acme.scaffold.system.file;

import java.util.List;

/**
 * 文件统计总览（V50）：全量计数 + 占用空间 + 按来源模块拆分（按文件数降序）。
 *
 * @param totalCount 未删记录总数
 * @param totalSize  占用字节数合计
 * @param modules    按来源模块拆分（按文件数降序）
 */
public record FileStatsView(
        long totalCount,
        long totalSize,
        List<ModuleFileStat> modules) {
}
