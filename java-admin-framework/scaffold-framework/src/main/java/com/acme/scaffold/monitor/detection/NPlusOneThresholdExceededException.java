package com.acme.scaffold.monitor.detection;

/**
 * N+1 阈值超限异常（ST5）：当 {@link NPlusOneProbe} 处于 fail 模式且单请求内同构 SQL 执行跨越阈值时抛出，
 * 用于 CI/集成测试门禁——将「N+1 反模式」从「告警」升级为「失败」，阻断带回归风险的合并。
 *
 * <p>默认不启用（fail 模式关闭），生产环境仅告警不阻断；仅在「测试 / IT」profile 下开启，
 * 由 jOOQ 执行监听器 {@link NPlusOneListener} 在阈值跨越点抛出。</p>
 */
public class NPlusOneThresholdExceededException extends RuntimeException {

    public NPlusOneThresholdExceededException(int threshold, String fingerprint) {
        super("检测到 N+1 查询反模式：单请求内同构 SQL 执行达 " + threshold
                + " 次（指纹=" + fingerprint + "）。请改用批量查询 / 预加载，避免随数据量放大的查询风暴。");
    }
}
