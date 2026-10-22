package com.acme.scaffold.common.messaging.domain;

/**
 * 信封规则（纯函数）：序号补洞判定与 msgId 生成校验。
 *
 * <p>对应设计稿 §3.6「重连恢复」：客户端携带 last_seq 重连，服务端按用户维度补洞；
 * 系统不承诺全局有序，跨分区/跨组顺序由应用层 seq 重组（§4.6）。
 */
public final class EnvelopeRules {

    private EnvelopeRules() {
    }

    /**
     * 判定 seq 是否相对上一次确认水位形成「空洞」（需要补洞）。
     *
     * <p>空洞判定：{@code seq > lastAcked + 1} 即中间有缺失；
     * {@code seq == lastAcked + 1} 为连续；{@code seq <= lastAcked} 为重复（幂等丢弃）。
     *
     * @param lastAcked 上一次已确认水位（首条消息前为 0）
     * @param seq       当前到达的 seq
     * @return 补洞缺口条数；0 表示无空洞
     */
    public static long gapAfter(long lastAcked, long seq) {
        if (seq <= lastAcked) {
            return 0; // 重复或滞后：不视为空洞（调用方按幂等处理）
        }
        return seq - lastAcked - 1;
    }

    /** 是否为重复/滞后消息（幂等去重判定，§4.6）。 */
    public static boolean isDuplicate(long lastAcked, long seq) {
        return seq <= lastAcked;
    }

    /** 序号推进：返回新的水位（单调不减，防回退）。 */
    public static long advanceWatermark(long current, long seq) {
        return Math.max(current, seq);
    }
}
