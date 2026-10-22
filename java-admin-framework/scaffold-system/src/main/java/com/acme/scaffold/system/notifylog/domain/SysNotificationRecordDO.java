package com.acme.scaffold.system.notifylog.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知发送记录实体（对应 {@code sys_notification_record}，DDL 见 V42__notification_record.sql）。
 *
 * <p>{@code NotificationService.dispatch} 每次投递尝试（含被拒与失败）落一条，
 * 覆盖 IN_APP / WEBHOOK / WEBSOCKET / EMAIL / SMS 全五渠道——
 * 短信渠道另有一份网关对账面的 {@code sys_sms_record}，两表口径差异见开发手册 M12 章节。</p>
 *
 * <p><b>摘要 + 掩码存储</b>：标题/正文仅存摘要（去控制符 + 截断），接收方按渠道掩码
 * （手机号 {@code 138****8000}、邮箱 {@code z***@example.com}、webhook 地址抹除 query），
 * 与短信记录同范式——PII 不明文落库，页面查询无需再做视图层脱敏。</p>
 */
@Data
public class SysNotificationRecordDO {

    private Long id;

    private Long tenantId = 0L;

    /** 渠道：{@code Channel} 枚举名（IN_APP / WEBHOOK / WEBSOCKET / EMAIL / SMS）。 */
    private String channel;

    /** 来源模板编码（M11 按模板发送时回填；原始直发为 null）。 */
    private String templateCode;

    /** 消息类型（取自模板或调用方，如 NOTICE / ALERT）。 */
    private String msgType;

    /** 标题摘要（短信等无标题渠道为空串）。 */
    private String titleSummary;

    /** 正文摘要（去 CR/LF/TAB、trim、超长截断加省略号）。 */
    private String contentSummary;

    /** 接收方摘要（按渠道掩码，如 {@code 138****8000,139****9000} / {@code z***@example.com} / {@code 广播}）。 */
    private String receiverSummary;

    /** 目标接收数（短信/邮件为地址数；IN_APP/WEBSOCKET 为定向用户数，广播记 0；WEBHOOK 记 1）。 */
    private Integer receiverCount;

    /** 实际投递单元数（适配器返回值；被拒或失败为 0）。 */
    private Integer delivered;

    /** 结果：SUCCESS / FAILED。 */
    private String status;

    /** 失败原因（异常消息，截断至 512 字符；成功为 null）。 */
    private String errorMessage;

    /** 操作人 ID（无登录上下文的内部触发为 null，如周期任务、告警 webhook）。 */
    private Long operatorId;

    /** 操作人显示名（冗余存储，与登录日志同范式）。 */
    private String operatorName;

    /** 链路追踪 ID。 */
    private String traceId;

    /** 发送时间（落库时间）。 */
    private LocalDateTime sentAt;
}
