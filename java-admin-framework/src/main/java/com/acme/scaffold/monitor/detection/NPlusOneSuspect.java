package com.acme.scaffold.monitor.detection;

/**
 * N+1 疑似事件：{@link NPlusOneProbe} 判定某指纹同构 SQL 在单线程（通常对应一次请求）内
 * 执行达阈值时，由 {@link NPlusOneListener} 登记一条事件，交由运维侧查询与复盘。
 *
 * @param fingerprint      同构指纹（{@link NPlusOneProbe#normalize(String)} 归一化结果）
 * @param sampleSql        触发时的原始 SQL 样本（超长截断，便于人工定位代码位置）
 * @param count            触发时的执行次数（等于当时的阈值）
 * @param threadName       执行线程名（请求线程 / 任务线程，辅助判断触发场景）
 * @param timestampMillis  登记时间（epoch 毫秒）
 */
public record NPlusOneSuspect(String fingerprint, String sampleSql, int count, String threadName, long timestampMillis) {
}
