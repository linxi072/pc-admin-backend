package com.acme.scaffold.system.dto;

/**
 * 同步（把扫描结果写入库）的执行结果。
 *
 * @param insertedCount 新增条数
 * @param updatedCount  更新条数
 * @param unchangedCount 无变化、未触碰的条数
 * @param skippedCount  跳过的条数（无权限码且非白名单路径等不落库场景）
 * @param orphanCount   库中失效但保留的条数（不自动删除）
 */
public record ApiResourceSyncResult(long insertedCount, long updatedCount,
                                    long unchangedCount, long skippedCount, long orphanCount) {

    /** 实际写入库的总条数。 */
    public long affectedCount() {
        return insertedCount + updatedCount;
    }
}
