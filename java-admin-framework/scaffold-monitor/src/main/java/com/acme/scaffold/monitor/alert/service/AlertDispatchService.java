package com.acme.scaffold.monitor.alert.service;

import com.acme.scaffold.monitor.alert.AlertInboxEntry;
import com.acme.scaffold.monitor.alert.rules.AlertRuleRules;
import com.acme.scaffold.monitor.entity.AlertRuleDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警出站分发服务：将触发的规则告警可选地推送至配置的 Webhook 地址。
 *
 * <p>仅当规则渠道类型为 {@code WEBHOOK} 且 {@code channelConfig} 非空时出站；
 * 其余渠道（{@code INBOX}）仅入收件箱，不发网络请求。出站使用带超时（连接 3s / 读取 5s）
 * 的 {@link RestTemplate}，失败仅记日志不阻断评估链路——告警入收件箱的事实不受影响。</p>
 */
@Slf4j
@Service
public class AlertDispatchService {

    private final RestTemplate restTemplate;

    public AlertDispatchService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 分发触发的告警到出站渠道。
     *
     * @param rule   命中的规则（含渠道类型与配置）
     * @param entry  合成告警条目（入收件箱的同一条）
     * @param actual 命中时的实际指标值（随报文外发便于接收端定位）
     */
    public void dispatch(AlertRuleDO rule, AlertInboxEntry entry, BigDecimal actual) {
        if (AlertRuleRules.ChannelType.WEBHOOK != AlertRuleRules.parseChannelType(rule.getChannelType())) {
            return;
        }
        String url = rule.getChannelConfig();
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ruleName", rule.getRuleName());
            body.put("metricKey", rule.getMetricKey());
            body.put("actual", actual == null ? null : actual.toPlainString());
            body.put("threshold", rule.getThreshold() == null ? null : rule.getThreshold().toPlainString());
            body.put("severity", entry.getSeverity());
            body.put("summary", entry.getSummary());
            body.put("firedAt", entry.getReceivedAt() == null ? null : entry.getReceivedAt().toEpochMilli());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            log.info("告警规则[{}]出站 Webhook 分发成功: {}", rule.getRuleName(), url);
        } catch (Exception e) {
            log.warn("告警规则[{}]出站 Webhook 分发失败: {}", rule.getRuleName(), url, e);
        }
    }
}
