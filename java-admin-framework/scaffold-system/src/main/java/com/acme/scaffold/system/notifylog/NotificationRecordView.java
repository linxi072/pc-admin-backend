package com.acme.scaffold.system.notifylog;

import com.acme.scaffold.system.notifylog.domain.SysNotificationRecordDO;

import java.time.LocalDateTime;

/**
 * 通知发送记录视图（V42 通知记录页数据源，对应 {@code sys_notification_record} 表）。
 *
 * <p>入库前已完成脱敏摘要（标题/正文摘要化、接收方按渠道掩码），故本视图为直出映射，
 * <b>不提供</b>「敏感原文」开关——通知原文与明文 PII 不落库是该模块的安全前提。</p>
 *
 * @param id              记录 ID
 * @param channel         渠道：IN_APP / WEBHOOK / WEBSOCKET / EMAIL / SMS
 * @param templateCode    来源模板编码（原始直发为 null）
 * @param msgType         消息类型
 * @param titleSummary    标题摘要
 * @param contentSummary  正文摘要
 * @param receiverSummary 接收方摘要（按渠道掩码）
 * @param receiverCount   目标接收数
 * @param delivered       实际投递单元数
 * @param status          结果：SUCCESS / FAILED
 * @param errorMessage    失败原因（成功为 null）
 * @param operatorId      操作人 ID（内部触发为 null）
 * @param operatorName    操作人显示名
 * @param traceId         链路追踪 ID
 * @param sentAt          发送时间
 */
public record NotificationRecordView(
        Long id,
        String channel,
        String templateCode,
        String msgType,
        String titleSummary,
        String contentSummary,
        String receiverSummary,
        int receiverCount,
        int delivered,
        String status,
        String errorMessage,
        Long operatorId,
        String operatorName,
        String traceId,
        LocalDateTime sentAt) {

    /** 由持久化对象构造视图（null 返回 null）。 */
    public static NotificationRecordView of(SysNotificationRecordDO d) {
        if (d == null) {
            return null;
        }
        return new NotificationRecordView(d.getId(), d.getChannel(), d.getTemplateCode(), d.getMsgType(),
                d.getTitleSummary(), d.getContentSummary(), d.getReceiverSummary(),
                d.getReceiverCount() == null ? 0 : d.getReceiverCount(),
                d.getDelivered() == null ? 0 : d.getDelivered(),
                d.getStatus(), d.getErrorMessage(), d.getOperatorId(), d.getOperatorName(),
                d.getTraceId(), d.getSentAt());
    }
}
