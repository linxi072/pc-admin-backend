package com.acme.scaffold.system.dto;

/**
 * 通知中心未读汇总视图。
 *
 * @param announcementUnread 当前用户未读公告数（懒插入反连接推导，见 AnnouncementReadService）
 * @param messageUnread     当前用户未读站内信数（eager 投递，见 MessageService）
 * @param totalUnread       合计 = announcementUnread + messageUnread（角标展示值，前端自行钳制上限）
 */
public record NotificationSummaryView(long announcementUnread, long messageUnread, long totalUnread) {
}
