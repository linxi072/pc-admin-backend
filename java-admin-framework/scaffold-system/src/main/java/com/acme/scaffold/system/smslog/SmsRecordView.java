package com.acme.scaffold.system.smslog;

import com.acme.scaffold.system.smslog.domain.SysSmsRecordDO;

import java.time.LocalDateTime;

/**
 * 短信发送记录视图（V37 短信记录页数据源，对应 {@code sys_sms_record} 表）。
 *
 * <p>入库前已完成脱敏（手机号掩码、正文摘要化），故本视图为直出映射，
 * <b>不提供</b>「敏感原文」开关——与登录日志 {@code IP} 的默认掩码不同，
 * 短信记录的原文（号码/正文）根本不落库，从设计上消除越权读取面。</p>
 *
 * @param id            记录 ID
 * @param batchNo       批次号（与网关账单核对用）
 * @param mobilesMasked 接收号码掩码列表（逗号拼接，超宽截断）
 * @param mobileCount   接收号码数（权威口径）
 * @param contentSummary 正文摘要（去控制符 + 截断）
 * @param provider      网关实现：LOG / HTTP
 * @param status        结果：SUCCESS / FAILED
 * @param errorMessage  失败原因（成功为 null）
 * @param operatorId    操作人 ID（内部触发为 null）
 * @param operatorName  操作人显示名
 * @param traceId       链路追踪 ID
 * @param sentAt        发送时间
 */
public record SmsRecordView(
        Long id,
        String batchNo,
        String mobilesMasked,
        int mobileCount,
        String contentSummary,
        String provider,
        String status,
        String errorMessage,
        Long operatorId,
        String operatorName,
        String traceId,
        LocalDateTime sentAt) {

    /** 由持久化对象构造视图（null 返回 null）。 */
    public static SmsRecordView of(SysSmsRecordDO d) {
        if (d == null) {
            return null;
        }
        return new SmsRecordView(d.getId(), d.getBatchNo(), d.getMobilesMasked(),
                d.getMobileCount() == null ? 0 : d.getMobileCount(), d.getContentSummary(),
                d.getProvider(), d.getStatus(), d.getErrorMessage(), d.getOperatorId(),
                d.getOperatorName(), d.getTraceId(), d.getSentAt());
    }
}
