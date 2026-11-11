package com.acme.scaffold.monitor.alert.scheduler;

import com.acme.scaffold.monitor.alert.AlertProperties;
import com.acme.scaffold.monitor.alert.service.AlertRuleService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Component;

/**
 * 告警规则周期评估调度器。
 *
 * <p>由 {@code @EnableScheduling}（MonitoringConfig）提供调度基础设施，
 * 按 {@code monitoring.alert.rule.cron}（默认每分钟）触发；仅当
 * {@code monitoring.alert.rule.enabled=true} 时实际评估，默认关闭以免无规则时空转。</p>
 */
@Component
@RequiredArgsConstructor
public class AlertRuleScheduler {

    private final AlertRuleService ruleService;
    private final AlertProperties alertProperties;

    @Scheduled(cron = "${monitoring.alert.rule.cron:0 */1 * * * *}")
    public void tick() {
        if (!alertProperties.getRule().isEnabled()) {
            return;
        }
        ruleService.evaluateTick(Instant.now());
    }
}
