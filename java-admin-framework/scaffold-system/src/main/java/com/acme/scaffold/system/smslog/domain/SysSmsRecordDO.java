package com.acme.scaffold.system.smslog.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信发送记录实体（对应 {@code sys_sms_record}，DDL 见 V37__sms_record.sql）。
 *
 * <p>短信渠道（{@code Channel.SMS}）每次投递（含网关失败）落一条，为运维提供对账与排查面：
 * 谁在何时给哪些号码（掩码）发了什么（摘要）、经由哪个网关、成功还是失败、失败原因。</p>
 *
 * <p><b>脱敏存储</b>：手机号以 {@link com.acme.scaffold.common.sensitive.SensitiveMasker#maskPhone}
 * 掩码后落库（{@code 138****8000}），正文仅存摘要（去控制符 + 截断），
 * 与登录日志 IP 掩码同范式——PII 不明文落库，号码数以 {@code mobileCount} 为权威口径。</p>
 */
@Data
public class SysSmsRecordDO {

    private Long id;

    private Long tenantId = 0L;

    /** 批次号（一次 dispatch 一个批次，取雪花 ID 字符串，便于与网关账单核对）。 */
    private String batchNo;

    /** 接收手机号掩码列表（逗号拼接，超列宽截断；号码数见 mobileCount）。 */
    private String mobilesMasked;

    /** 接收号码数（去重后，权威口径）。 */
    private Integer mobileCount;

    /** 短信正文摘要（去 CR/LF/TAB、trim、超长截断加省略号）。 */
    private String contentSummary;

    /** 网关实现：LOG（开发默认）/ HTTP（通用 HTTP 网关）。 */
    private String provider;

    /** 结果：SUCCESS / FAILED。 */
    private String status;

    /** 失败原因（网关异常消息，截断至 512 字符；成功为 null）。 */
    private String errorMessage;

    /** 操作人 ID（无登录上下文的内部触发为 null，如告警 webhook）。 */
    private Long operatorId;

    /** 操作人显示名（冗余存储，与登录日志同范式）。 */
    private String operatorName;

    /** 链路追踪 ID。 */
    private String traceId;

    /** 发送时间（落库时间）。 */
    private LocalDateTime sentAt;
}
