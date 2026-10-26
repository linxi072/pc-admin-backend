package com.acme.scaffold.system.dashboard;

import java.time.LocalDate;
import java.util.List;

/**
 * 运营趋势快照（M27 报表大屏 / 时间序列，第二刀）。
 *
 * <p>包含近 {@code days} 日的三条时间序列：新增用户、新增租户、活跃租户（按登录活动去重统计）。
 * 所有序列已由 {@link DashboardRules#fillDailyGaps} 补齐为连续日期（缺数据日计 0），
 * 前端可直接按索引渲染折线/柱状图，无需再做日期对齐。</p>
 *
 * @param days           回溯天数（已钳制到 [1, 90]）
 * @param newUsers       每日新增用户（连续、长度 = days）
 * @param newTenants     每日新增租户（连续、长度 = days）
 * @param activeTenants  每日活跃租户数（按 occurred_at 去重 tenant_id，连续、长度 = days）
 * @param generatedAt    快照生成时刻（epoch 毫秒）
 */
public record DashboardTrend(
        int days,
        List<TrendPoint> newUsers,
        List<TrendPoint> newTenants,
        List<TrendPoint> activeTenants,
        long generatedAt) {
}
