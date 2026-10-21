package com.acme.scaffold.system.smslog;

import com.acme.scaffold.common.IdGenerator;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.system.smslog.domain.SysSmsRecordDO;
import com.acme.scaffold.system.smslog.rules.SmsRecordRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 短信发送记录器：短信渠道每次投递（含网关失败）落入 {@code sys_sms_record}，
 * 为「短信记录」页与网关账单对账提供数据源。
 *
 * <p><b>独立事务（REQUIRES_NEW）</b>：与 {@code LoginLogRecorder} 同范式——记录写入挂起主事务、
 * 以新事务提交，避免调用方事务回滚把审计记录一并带走；写入失败不影响发送主流程（仅 warn 日志）。</p>
 *
 * <p><b>脱敏在入库前完成</b>（{@link SmsRecordRules}）：手机号掩码、正文摘要化，
 * 数据库中不存在明文 PII，页面查询无需再做视图层脱敏。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsRecordRecorder {

    /** 掩码号码列宽上限（{@code VARCHAR(1024)}）：100 个掩码号约 1200 字符，超出截断，号码数以 mobileCount 为准。 */
    private static final int MAX_MASKED_LEN = 1024;

    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;

    /**
     * 记录一次短信发送结果。
     *
     * @param batchNo      批次号（一次 dispatch 一个批次，供网关账单核对）
     * @param mobiles      归一后的接收号码列表（落库前掩码）
     * @param content      发送正文（已含签名拼接，落库前摘要化）
     * @param provider     网关实现名（LOG / HTTP）
     * @param success      是否发送成功
     * @param errorMessage 失败原因（成功传 null）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String batchNo, List<String> mobiles, String content,
                       String provider, boolean success, String errorMessage) {
        try {
            SysSmsRecordDO row = new SysSmsRecordDO();
            row.setBatchNo(SmsRecordRules.truncate(batchNo, 64));
            row.setMobilesMasked(SmsRecordRules.truncate(SmsRecordRules.maskMobiles(mobiles), MAX_MASKED_LEN));
            row.setMobileCount(mobiles == null ? 0 : mobiles.size());
            row.setContentSummary(SmsRecordRules.summarizeContent(content));
            row.setProvider(SmsRecordRules.truncate(provider, 16));
            row.setStatus(success ? SmsRecordRules.STATUS_SUCCESS : SmsRecordRules.STATUS_FAILED);
            row.setErrorMessage(SmsRecordRules.truncate(errorMessage, 512));
            // 操作人：无登录上下文（告警 webhook 等内部触发）时留空，不影响记录完整性
            securityContextFacade.getCurrentPrincipal().ifPresent(p -> {
                row.setOperatorId(p.userId());
                row.setOperatorName(SmsRecordRules.truncate(p.displayName(), 64));
            });
            row.setTraceId(MDC.get("traceId"));
            row.setSentAt(LocalDateTime.now());
            JooqWriters.insert(dsl, JooqTables.SYS_SMS_RECORD, row);
        } catch (Exception e) {
            // 短信记录属审计旁路，写入失败不阻断发送主流程（短信已真实发出，记录缺失可补）
            log.warn("短信发送记录写入失败 batchNo={} success={} err={}", batchNo, success, e.getMessage());
        }
    }

    /** 生成批次号（雪花 ID 字符串，与行 id 同源生成器，保证全局唯一）。 */
    public static String newBatchNo() {
        return String.valueOf(IdGenerator.nextId());
    }
}
