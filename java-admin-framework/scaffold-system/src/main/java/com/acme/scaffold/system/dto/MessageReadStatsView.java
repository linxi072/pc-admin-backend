package com.acme.scaffold.system.dto;

import com.acme.scaffold.system.entity.SysMessageDO;
import com.acme.scaffold.system.rules.MessageReadRules;

/**
 * 站内信已读统计视图（已读率分子分母与推导值一次齐备，前端不做算术）。
 *
 * <p>分母口径：eager 投递模型下接收人总数即回执表行数（{@code totalCount}），
 * 已读数取回执表 {@code is_read = 1} 的行数（{@code readCount}）。本视图只承载
 * 展示所需字段，推导统一走 {@link MessageReadRules}，保证各调用点口径一致。
 *
 * @param totalCount  接收人总数（回执表全部行数）
 * @param readCount   已读人数（回执表 is_read = 1 的行数）
 * @param unreadCount 未读人数（= totalCount - readCount，已归一到 >= 0）
 * @param readRate    已读率（百分比，一位小数；分母缺失时为 0.0）
 */
public record MessageReadStatsView(Long messageId, String title, String msgType,
                                   Long senderId, String senderName,
                                   Integer totalCount, Integer readCount,
                                   Integer unreadCount, Double readRate) {

    /**
     * 由发件主表 + 已计算的收发计数构造统计视图。
     * 计数从回执表聚合（eager 模型源真值），比率与未读数推导走 {@link MessageReadRules}。
     */
    public static MessageReadStatsView of(SysMessageDO m, int totalCount, int readCount, String senderName) {
        int total = Math.max(totalCount, 0);
        int read = Math.min(Math.max(readCount, 0), total);
        return new MessageReadStatsView(
                m.getId(), m.getTitle(), m.getMsgType(),
                m.getSenderId(), senderName,
                total, read,
                MessageReadRules.unreadCount(read, total),
                MessageReadRules.readRate(read, total));
    }
}
