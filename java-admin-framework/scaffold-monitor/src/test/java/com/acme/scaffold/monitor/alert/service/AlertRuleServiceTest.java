package com.acme.scaffold.monitor.alert.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.monitor.alert.AlertInboxEntry;
import com.acme.scaffold.monitor.alert.AlertReceiverService;
import com.acme.scaffold.monitor.alert.rules.AlertRuleRules;
import com.acme.scaffold.monitor.dto.AlertRuleCreateRequest;
import com.acme.scaffold.monitor.dto.AlertRuleUpdateRequest;
import com.acme.scaffold.monitor.dto.AlertRuleView;
import com.acme.scaffold.monitor.entity.AlertRuleDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警规则服务单测：CRUD 校验 + 评估引擎边沿触发逻辑。
 * 用 Mockito 桩替换 store / sampler / receiver / dispatcher，聚焦编排与状态机。
 */
@ExtendWith(MockitoExtension.class)
class AlertRuleServiceTest {

    @Mock
    private AlertRuleStore store;
    @Mock
    private MetricSampler sampler;
    @Mock
    private AlertReceiverService receiver;
    @Mock
    private AlertDispatchService dispatcher;

    @InjectMocks
    private AlertRuleService service;

    private AlertRuleDO enabledCpuRule() {
        AlertRuleDO d = new AlertRuleDO();
        d.setId(1L);
        d.setRuleName("CPU 高负载");
        d.setMetricKey("CPU_USAGE");
        d.setComparator("GT");
        d.setThreshold(new BigDecimal("80"));
        d.setSeverity("WARNING");
        d.setConsecutiveCount(1);
        d.setForDurationSec(0);
        d.setChannelType("INBOX");
        d.setEnabled(true);
        return d;
    }

    private void stubSampleBreached(boolean breached) {
        BigDecimal value = breached ? new BigDecimal("90") : new BigDecimal("50");
        when(sampler.sample(eq(AlertRuleRules.MetricKey.CPU_USAGE))).thenReturn(value);
    }

    // ---------------- CRUD ----------------

    @Test
    @DisplayName("create：合法规则调用 store.insert 并返回视图")
    void create_valid_inserts() {
        AlertRuleCreateRequest req = new AlertRuleCreateRequest("R", "CPU_USAGE", "GT",
                new BigDecimal("80"), "WARNING", 0, 1, "INBOX", null, true, "备注");
        AlertRuleView v = service.create(req);
        verify(store).insert(any(AlertRuleDO.class));
        assertEquals("R", v.ruleName());
        assertEquals("CPU_USAGE", v.metricKey());
    }

    @Test
    @DisplayName("create：非法规则抛出 BusinessException（400）")
    void create_invalid_throws() {
        AlertRuleCreateRequest req = new AlertRuleCreateRequest("", "BAD", "BAD",
                null, "BAD", 0, 0, "BAD", null, true, null);
        assertThrows(BusinessException.class, () -> service.create(req));
        verify(store, never()).insert(any());
    }

    @Test
    @DisplayName("get：不存在时抛 BusinessException")
    void get_notFound_throws() {
        when(store.findById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.get(1L));
    }

    @Test
    @DisplayName("list：返回视图列表")
    void list_mapsViews() {
        when(store.findAll()).thenReturn(List.of(enabledCpuRule()));
        assertEquals(1, service.list().size());
    }

    @Test
    @DisplayName("delete：不存在时抛 BusinessException")
    void delete_notFound_throws() {
        when(store.findById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.delete(1L));
    }

    @Test
    @DisplayName("setEnabled：更新 enabled 并落库")
    void setEnabled_updates() {
        AlertRuleDO existing = enabledCpuRule();
        when(store.findById(1L)).thenReturn(existing);
        service.setEnabled(1L, false);
        verify(store).update(eq(1L), any(AlertRuleDO.class));
    }

    // ---------------- 评估引擎：边沿触发 ----------------

    @Test
    @DisplayName("评估：首次超阈触发一次，持续超阈不再重复触发（边沿触发）")
    void evaluate_firesOnceThenSuppressed() {
        when(store.findEnabled()).thenReturn(List.of(enabledCpuRule()));
        stubSampleBreached(true);

        Instant t0 = Instant.parse("2026-10-20T10:00:00Z");
        int first = service.evaluateTick(t0);
        int second = service.evaluateTick(t0.plusSeconds(60));

        assertEquals(1, first);
        assertEquals(0, second);
        verify(receiver, times(1)).ingestSynthetic(any(AlertInboxEntry.class));
    }

    @Test
    @DisplayName("评估：指标恢复后解除 firing，再次超阈可重新触发")
    void evaluate_recoverThenRefire() {
        when(store.findEnabled()).thenReturn(List.of(enabledCpuRule()));
        Instant t0 = Instant.parse("2026-10-20T10:00:00Z");

        stubSampleBreached(true);
        assertEquals(1, service.evaluateTick(t0));
        assertEquals(0, service.evaluateTick(t0.plusSeconds(60))); // 持续超阈不再触发

        stubSampleBreached(false);
        service.evaluateTick(t0.plusSeconds(120)); // 恢复，状态复位

        stubSampleBreached(true);
        assertEquals(1, service.evaluateTick(t0.plusSeconds(180))); // 再次超阈重新触发
        verify(receiver, times(2)).ingestSynthetic(any(AlertInboxEntry.class));
    }

    @Test
    @DisplayName("评估：连续次数门控——consecutiveCount=2 时须连续两周期才触发")
    void evaluate_consecutiveGating() {
        AlertRuleDO rule = enabledCpuRule();
        rule.setConsecutiveCount(2);
        when(store.findEnabled()).thenReturn(List.of(rule));
        stubSampleBreached(true);

        Instant t0 = Instant.parse("2026-10-20T10:00:00Z");
        assertEquals(0, service.evaluateTick(t0));            // 连续 1 次，未达阈值
        assertEquals(1, service.evaluateTick(t0.plusSeconds(60))); // 连续 2 次，触发
        assertEquals(0, service.evaluateTick(t0.plusSeconds(120))); // 持续超阈不再触发
        verify(receiver, times(1)).ingestSynthetic(any());
    }

    @Test
    @DisplayName("评估：WEBHOOK 渠道命中时调用出站分发")
    void evaluate_webhookDispatch() {
        AlertRuleDO rule = enabledCpuRule();
        rule.setChannelType("WEBHOOK");
        rule.setChannelConfig("http://hook.local/alert");
        when(store.findEnabled()).thenReturn(List.of(rule));
        stubSampleBreached(true);

        assertEquals(1, service.evaluateTick(Instant.parse("2026-10-20T10:00:00Z")));
        verify(receiver).ingestSynthetic(any());
        verify(dispatcher).dispatch(eq(rule), any(AlertInboxEntry.class), any(BigDecimal.class));
    }

    @Test
    @DisplayName("评估：指标为 null（采集失败）视为不触发")
    void evaluate_nullMetricNoFire() {
        when(store.findEnabled()).thenReturn(List.of(enabledCpuRule()));
        when(sampler.sample(eq(AlertRuleRules.MetricKey.CPU_USAGE))).thenReturn(null);
        assertEquals(0, service.evaluateTick(Instant.parse("2026-10-20T10:00:00Z")));
        verify(receiver, never()).ingestSynthetic(any());
    }

    @Test
    @DisplayName("评估：合成告警 severity/summary 反映规则配置")
    void evaluate_entryReflectsRule() {
        when(store.findEnabled()).thenReturn(List.of(enabledCpuRule()));
        stubSampleBreached(true);
        service.evaluateTick(Instant.parse("2026-10-20T10:00:00Z"));

        ArgumentCaptor<AlertInboxEntry> captor = ArgumentCaptor.forClass(AlertInboxEntry.class);
        verify(receiver).ingestSynthetic(captor.capture());
        AlertInboxEntry e = captor.getValue();
        assertEquals("WARNING", e.getSeverity());
        assertEquals("[规则] CPU 高负载", e.getName());
        assertEquals("rules-engine", e.getInstance());
    }
}
