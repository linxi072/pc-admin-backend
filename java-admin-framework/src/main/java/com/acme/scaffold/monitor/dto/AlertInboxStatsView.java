package com.acme.scaffold.monitor.dto;

import java.util.Map;

/**
 * 告警收件箱汇总视图（管理端只读投影）。
 *
 * @param capacity                 收件箱容量（{@code monitoring.alert.inbox-capacity}，有界环形裁剪）
 * @param total                    当前留存条数
 * @param firingCount              firing 态条数
 * @param resolvedCount            resolved 态条数
 * @param severityCounts           severity 分布（计数降序、同数按键名序；缺失归 {@code unknown} 桶）
 * @param mostRecentEpochMillis    最新一条接收时间（epoch 毫秒）；空箱为 0
 * @param oldestRetainedEpochMillis 最旧一条留存时间（epoch 毫秒）；空箱为 0
 */
public record AlertInboxStatsView(int capacity, int total, int firingCount, int resolvedCount,
                                  Map<String, Integer> severityCounts, long mostRecentEpochMillis,
                                  long oldestRetainedEpochMillis) {
}
