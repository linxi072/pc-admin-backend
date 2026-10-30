package com.acme.scaffold.system.dto;

import java.time.LocalDateTime;

/**
 * 通知中心近期未读条目（公告未读 + 站内信未读合并后的统一条目）。
 *
 * @param source     来源：ANNOUNCEMENT（公告）/ MESSAGE（站内信）
 * @param id         来源主键（公告 id / 站内信 messageId）
 * @param title     条目标题
 * @param summary   内容摘要（后端已截断，去除多余空白）
 * @param link      点击跳转的相对路径提示（/system/notice?tab=...&id=...），前端可按自身路由改写
 * @param occurredAt 发生时间（公告取发布时间，站内信取发送时间），用于合并排序，可能为空
 */
public record NotificationItemView(String source, Long id, String title,
                                   String summary, String link, LocalDateTime occurredAt) {
}
