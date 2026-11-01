package com.acme.scaffold.monitor.alert.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.monitor.alert.AlertInboxEntry;
import com.acme.scaffold.monitor.alert.AlertReceiverService;
import com.acme.scaffold.monitor.alert.rules.AlertRuleRules;
import com.acme.scaffold.monitor.dto.AlertRuleCreateRequest;
import com.acme.scaffold.monitor.dto.AlertRuleUpdateRequest;
import com.acme.scaffold.monitor.dto.AlertRuleView;
import com.acme.scaffold.monitor.entity.AlertRuleDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警规则服务：CRUD 编排 + 周期评估引擎。
 *
 * <p><b>CRUD</b>：委托 {@link AlertRuleStore} 持久化，创建/更新前经
 * {@link AlertRuleRules#validate} 统一校验（字段错误以 {@code 400} 返回）。</p>
 *
 * <p><b>评估引擎</b>（{@link #evaluateTick}）：加载全部已启用规则，逐条采样指标 →
 * 阈值比较 → 边沿触发。为抑制「持续超阈时每周期重复灌入收件箱」，采用进程内
 * {@code BreachState} 记录连续超阈次数与首超时间，仅在「满足持续时长 + 连续次数 + 当前未 firing」
 * 三者齐备时触发一次，指标恢复正常后自动解除 firing 状态。</p>
 *
 * <p>触发动作：构造合成告警经 {@link AlertReceiverService#ingestSynthetic} 入既有收件箱
 * （与 Alertmanager 推送共用），并按渠道配置可选地出站 Webhook（{@link AlertDispatchService}）。</p>
 */
@Slf4j
@Service
public class AlertRuleService {

    private final AlertRuleStore store;
    private final MetricSampler sampler;
    private final AlertReceiverService receiver;
    private final AlertDispatchService dispatcher;

    /** 规则触发状态（进程内，重启丢失；仅用于边沿触发去重，不影响规则持久化）。 */
    private final Map<Long, BreachState> breachState = new ConcurrentHashMap<>();

    public AlertRuleService(AlertRuleStore store, MetricSampler sampler,
                            AlertReceiverService receiver, AlertDispatchService dispatcher) {
        this.store = store;
        this.sampler = sampler;
        this.receiver = receiver;
        this.dispatcher = dispatcher;
    }

    // ---------------- CRUD ----------------

    public List<AlertRuleView> list() {
        return store.findAll().stream().map(AlertRuleView::of).toList();
    }

    public AlertRuleView get(Long id) {
        AlertRuleDO d = store.findById(id);
        if (d == null) {
            throw BusinessException.badRequest("告警规则不存在: " + id);
        }
        return AlertRuleView.of(d);
    }

    public AlertRuleView create(AlertRuleCreateRequest req) {
        AlertRuleDO d = toDO(req);
        List<String> errs = AlertRuleRules.validate(d);
        if (!errs.isEmpty()) {
            throw BusinessException.badRequest(String.join("; ", errs));
        }
        store.insert(d);
        return AlertRuleView.of(d);
    }

    public AlertRuleView update(Long id, AlertRuleUpdateRequest req) {
        AlertRuleDO existing = store.findById(id);
        if (existing == null) {
            throw BusinessException.badRequest("告警规则不存在: " + id);
        }
        apply(existing, req);
        List<String> errs = AlertRuleRules.validate(existing);
        if (!errs.isEmpty()) {
            throw BusinessException.badRequest(String.join("; ", errs));
        }
        store.update(id, existing);
        return AlertRuleView.of(existing);
    }

    public void delete(Long id) {
        if (store.findById(id) == null) {
            throw BusinessException.badRequest("告警规则不存在: " + id);
        }
        store.delete(id);
        breachState.remove(id);
    }

    public AlertRuleView setEnabled(Long id, boolean enabled) {
        AlertRuleDO existing = store.findById(id);
        if (existing == null) {
            throw BusinessException.badRequest("告警规则不存在: " + id);
        }
        existing.setEnabled(enabled);
        store.update(id, existing);
        return AlertRuleView.of(existing);
    }

    // ---------------- 评估引擎 ----------------

    /**
     * 单次评估所有启用规则，返回本次新触发的告警条数。
     *
     * @param now 评估时刻（注入便于单测；生产由调度器传入 {@code Instant.now()}）
     */
    public int evaluateTick(Instant now) {
        int fired = 0;
        for (AlertRuleDO rule : store.findEnabled()) {
            if (rule.getId() == null) {
                continue;
            }
            AlertRuleRules.MetricKey mk = AlertRuleRules.parseMetricKey(rule.getMetricKey());
            AlertRuleRules.Comparator cmp = AlertRuleRules.parseComparator(rule.getComparator());
            if (mk == null || cmp == null) {
                continue;
            }
            BigDecimal actual = sampler.sample(mk);
            boolean breached = AlertRuleRules.compare(actual, cmp, rule.getThreshold());
            BreachState st = breachState.computeIfAbsent(rule.getId(), k -> new BreachState());
            if (!breached) {
                st.firing = false;
                st.consecutive = 0;
                st.firstBreachAt = null;
                continue;
            }
            st.consecutive++;
            if (st.firstBreachAt == null) {
                st.firstBreachAt = now;
            }
            int need = rule.getConsecutiveCount() == null ? 1 : rule.getConsecutiveCount();
            boolean sustained = st.consecutive >= need;
            long durSec = Duration.between(st.firstBreachAt, now).getSeconds();
            int forDur = rule.getForDurationSec() == null ? 0 : rule.getForDurationSec();
            boolean durOk = forDur <= 0 || durSec >= forDur;
            if (sustained && durOk && !st.firing) {
                AlertInboxEntry entry = AlertRuleRules.buildEntry(rule, actual, now);
                receiver.ingestSynthetic(entry);
                dispatcher.dispatch(rule, entry, actual);
                st.firing = true;
                fired++;
                log.info("告警规则触发 name={} metric={} actual={} threshold={}",
                        rule.getRuleName(), mk, actual, rule.getThreshold());
            }
        }
        return fired;
    }

    // ---------------- 映射 ----------------

    private AlertRuleDO toDO(AlertRuleCreateRequest req) {
        AlertRuleDO d = new AlertRuleDO();
        d.setRuleName(req.ruleName());
        d.setMetricKey(req.metricKey());
        d.setComparator(req.comparator());
        d.setThreshold(req.threshold());
        d.setSeverity(req.severity());
        d.setForDurationSec(req.forDurationSec() == null ? 0 : req.forDurationSec());
        d.setConsecutiveCount(req.consecutiveCount() == null ? 1 : req.consecutiveCount());
        d.setChannelType(req.channelType());
        d.setChannelConfig(req.channelConfig());
        d.setEnabled(req.enabled() == null ? Boolean.FALSE : req.enabled());
        d.setRemark(req.remark());
        return d;
    }

    private void apply(AlertRuleDO d, AlertRuleUpdateRequest req) {
        if (req.ruleName() != null) {
            d.setRuleName(req.ruleName());
        }
        if (req.metricKey() != null) {
            d.setMetricKey(req.metricKey());
        }
        if (req.comparator() != null) {
            d.setComparator(req.comparator());
        }
        if (req.threshold() != null) {
            d.setThreshold(req.threshold());
        }
        if (req.severity() != null) {
            d.setSeverity(req.severity());
        }
        if (req.forDurationSec() != null) {
            d.setForDurationSec(req.forDurationSec());
        }
        if (req.consecutiveCount() != null) {
            d.setConsecutiveCount(req.consecutiveCount());
        }
        if (req.channelType() != null) {
            d.setChannelType(req.channelType());
        }
        if (req.channelConfig() != null) {
            d.setChannelConfig(req.channelConfig());
        }
        if (req.enabled() != null) {
            d.setEnabled(req.enabled());
        }
        if (req.remark() != null) {
            d.setRemark(req.remark());
        }
    }

    /** 单条规则的边沿触发状态（进程内）。 */
    private static final class BreachState {
        boolean firing = false;
        int consecutive = 0;
        Instant firstBreachAt = null;
    }
}
