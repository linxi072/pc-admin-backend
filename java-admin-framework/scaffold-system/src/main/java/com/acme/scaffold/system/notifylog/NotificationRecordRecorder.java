package com.acme.scaffold.system.notifylog;

import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.notifylog.domain.SysNotificationRecordDO;
import com.acme.scaffold.system.notifylog.rules.NotificationRecordRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 通知发送记录器：{@code NotificationService.dispatch} 每次投递尝试（含被拒与失败）
 * 落入 {@code sys_notification_record}，为「通知记录」页提供全五渠道统一审计面。
 *
 * <p><b>独立事务（REQUIRES_NEW）</b>：与 {@code SmsRecordRecorder}/{@code LoginLogRecorder} 同范式——
 * 记录写入以新事务提交，避免调用方事务回滚把审计记录一并带走；写入失败不影响发送主流程
 * （仅 warn 日志）。注意：失败路径上主流程即将抛异常，若两者共事务会连带回滚记录，
 * REQUIRES_NEW 是「失败也要留痕」成立的前提。</p>
 *
 * <p><b>摘要 + 掩码在入库前完成</b>（{@link NotificationRecordRules}）：标题/正文摘要化、
 * 接收方按渠道掩码（手机号/邮箱/webhook query），数据库中不存在明文 PII。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRecordRecorder {

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;

    /**
     * 记录一次通知投递尝试。
     *
     * @param message      被投递的渠道无关消息（接收方摘要与目标数由此推导，方法内不改动入参）
     * @param templateCode 来源模板编码（原始直发传 null）
     * @param delivered    实际投递单元数（适配器返回值；被拒或失败传 0）
     * @param success      是否投递成功
     * @param errorMessage 失败原因（成功传 null）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(NotificationMessage message, String templateCode, int delivered,
                       boolean success, String errorMessage) {
        try {
            SysNotificationRecordDO row = new SysNotificationRecordDO();
            row.setChannel(message.channel() == null
                    ? NotificationRecordRules.CHANNEL_UNKNOWN : message.channel().name());
            row.setTemplateCode(NotificationRecordRules.truncate(templateCode, 64));
            row.setMsgType(NotificationRecordRules.truncate(message.msgType(), 32));
            row.setTitleSummary(NotificationRecordRules.summarizeTitle(message.title()));
            row.setContentSummary(resolveContentSummary(message));
            row.setReceiverSummary(NotificationRecordRules.receiverSummary(message));
            row.setReceiverCount(NotificationRecordRules.receiverCount(message));
            row.setDelivered(delivered);
            row.setStatus(success ? NotificationRecordRules.STATUS_SUCCESS : NotificationRecordRules.STATUS_FAILED);
            row.setErrorMessage(NotificationRecordRules.truncate(errorMessage, 512));
            // 操作人：无登录上下文（周期任务、告警 webhook 等内部触发）时留空，不影响记录完整性
            securityContextFacade.getCurrentPrincipal().ifPresent(p -> {
                row.setOperatorId(p.userId());
                row.setOperatorName(NotificationRecordRules.truncate(p.displayName(), 64));
            });
            row.setTraceId(MDC.get("traceId"));
            row.setSentAt(LocalDateTime.now());
            JooqWriters.insert(dsl, JooqTables.SYS_NOTIFICATION_RECORD, row);
        } catch (Exception e) {
            // 通知记录属审计旁路，写入失败不阻断发送主流程（消息已真实投递，记录缺失可补）
            log.warn("通知发送记录写入失败 channel={} success={} err={}",
                    message.channel(), success, e.getMessage());
        }
    }

    /**
     * 正文摘要口径：正文优先；正文空白且 extra 非空时（WEBHOOK 常见）回退 extra 摘要，
     * 保证无正文渠道也有排障信息。
     */
    private String resolveContentSummary(NotificationMessage message) {
        String summary = NotificationRecordRules.summarizeContent(message.content());
        if (!summary.isEmpty()) {
            return summary;
        }
        return NotificationRecordRules.summarizeExtra(message.extra());
    }
}
