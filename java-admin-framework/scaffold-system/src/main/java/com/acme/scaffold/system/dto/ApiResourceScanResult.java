package com.acme.scaffold.system.dto;

import java.util.List;

/**
 * 扫描与库内现有数据的差异结果，用于前端「扫描预览」。
 *
 * <p>变更类型：
 * <ul>
 *   <li>{@link #NEW}：库中不存在，需新增</li>
 *   <li>{@link #CHANGED}：库中已存在，但权限码/资源名/鉴权模式有变化，需更新</li>
 *   <li>{@link #ORPHANED}：库中存在但代码里已无对应接口（接口被删除），<b>不会</b>被自动删除</li>
 * </ul>
 *
 * @param newResources      待新增
 * @param changedResources  待更新
 * @param orphanedResources 失效（代码中已不存在）
 * @param unchangedCount    完全一致的数量
 * @param total             扫描到的接口总数
 */
public record ApiResourceScanResult(List<ScannedApiResource> newResources,
                                    List<ScannedApiResource> changedResources,
                                    List<ApiResourceView> orphanedResources,
                                    long unchangedCount,
                                    long total) {

    /** 待写入（新增 + 更新）的总条数。 */
    public long pendingCount() {
        return newResources.size() + changedResources.size();
    }
}
