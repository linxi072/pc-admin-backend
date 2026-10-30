package com.acme.scaffold.system.dto;

import com.acme.scaffold.system.rules.AnnouncementReadRules;

/**
 * 公告已读统计视图（已读率分子分母与推导值一次齐备，前端不做算术）。
 *
 * @param audienceCount 分母：发布时活跃用户快照；{@code <= 0} 表示分母缺失（V40 前存量公告），
 *                      此时 unreadCount/readRate 无意义，前端按「暂无统计」展示
 */
public record AnnouncementReadStatsView(Long announcementId, String title, String status,
                                        Integer audienceCount, Integer readCount,
                                        Integer unreadCount, Double readRate) {

    /**
     * 由公告实体构造统计视图；比率与未读数推导统一走
     * {@link AnnouncementReadRules}，保证与后端其他调用点口径一致。
     */
    public static AnnouncementReadStatsView of(com.acme.scaffold.system.entity.SysAnnouncementDO a) {
        int audience = a.getAudienceCount() == null ? 0 : a.getAudienceCount();
        int read = a.getReadCount() == null ? 0 : a.getReadCount();
        return new AnnouncementReadStatsView(a.getId(), a.getTitle(), a.getStatus(),
                audience, read,
                AnnouncementReadRules.unreadCount(read, audience),
                AnnouncementReadRules.readRate(read, audience));
    }
}
