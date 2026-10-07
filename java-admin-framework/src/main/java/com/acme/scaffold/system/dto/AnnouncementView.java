package com.acme.scaffold.system.dto;

import java.time.LocalDateTime;

/**
 * 公告视图。
 *
 * @param effective 是否当前可见（status=PUBLISHED 且在有效期内）——列表「我的公告」据此过滤
 */
public record AnnouncementView(Long id, String title, String content, String status, Integer isTop,
                               LocalDateTime publishAt, LocalDateTime expireAt,
                               LocalDateTime publishedAt, LocalDateTime offlineAt,
                               Long publisherId, Integer viewCount, boolean effective,
                               LocalDateTime createdAt) {

    public static AnnouncementView from(com.acme.scaffold.system.entity.SysAnnouncementDO a, boolean effective) {
        return new AnnouncementView(a.getId(), a.getTitle(), a.getContent(), a.getStatus(), a.getIsTop(),
                a.getPublishAt(), a.getExpireAt(), a.getPublishedAt(), a.getOfflineAt(),
                a.getPublisherId(), a.getViewCount(), effective, a.getCreatedAt());
    }

    /** 按当前时间判定是否处于有效期且已发布。 */
    public static boolean isEffective(com.acme.scaffold.system.entity.SysAnnouncementDO a, LocalDateTime now) {
        if (!"PUBLISHED".equals(a.getStatus())) {
            return false;
        }
        if (a.getPublishAt() != null && a.getPublishAt().isAfter(now)) {
            return false; // 定时生效尚未到达
        }
        return a.getExpireAt() == null || a.getExpireAt().isAfter(now);
    }
}
