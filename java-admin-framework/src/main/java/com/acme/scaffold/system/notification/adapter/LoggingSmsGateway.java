package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.sensitive.SensitiveMasker;
import com.acme.scaffold.system.notification.port.SmsGateway;
import com.acme.scaffold.system.notification.rules.SmsRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 开发默认短信网关：不真实发送，仅输出脱敏日志（手机号掩码中间四位，
 * 复用 {@link SensitiveMasker#maskPhone}，与登录日志脱敏策略一致）。
 *
 * <p>适用于开发 / 联调 / CI 环境：验证渠道编排与报文组装而无需网关凭据与费用。
 * 生产环境配置 {@code app.notifications.sms.provider=HTTP} 切换到真实网关。
 */
@Component
@ConditionalOnProperty(name = "app.notifications.sms.provider", havingValue = "LOG", matchIfMissing = true)
public class LoggingSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsGateway.class);

    @Override
    public void send(SmsRules.SmsDraft draft) {
        String masked = draft.mobiles().stream()
                .map(SensitiveMasker::maskPhone)
                .collect(Collectors.joining(","));
        log.info("SMS[LOG] to=[{}] content={}", masked, draft.content());
    }
}
