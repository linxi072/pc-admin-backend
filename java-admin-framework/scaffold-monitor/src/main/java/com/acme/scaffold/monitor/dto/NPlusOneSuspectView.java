package com.acme.scaffold.monitor.dto;

/**
 * N+1 疑似事件视图（只读投影，供运维查询与复盘）。
 *
 * @param fingerprint      同构指纹（参数已归一化为占位符，可直接用于定位代码中的查询）
 * @param sampleSql        触发时的原始 SQL 样本（超长截断）
 * @param count            触发时的执行次数
 * @param threadName       执行线程名（请求线程 / 任务线程）
 * @param timestampMillis  登记时间（epoch 毫秒）
 */
public record NPlusOneSuspectView(String fingerprint, String sampleSql, int count, String threadName, long timestampMillis) {
}
