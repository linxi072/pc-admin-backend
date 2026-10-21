package com.acme.scaffold.monitor.alert.service;

import com.acme.scaffold.monitor.alert.AlertInboxEntry;
import com.acme.scaffold.monitor.alert.AlertReceiverService;
import com.acme.scaffold.monitor.dto.AlertInboxItemView;
import com.acme.scaffold.monitor.dto.AlertInboxStatsView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警收件箱服务离线单测：筛选读取、limit 自适应、汇总与清空委托。
 *
 * <p>{@link AlertReceiverService} 以 Mockito 桩接——服务只消费
 * {@code inbox() / capacity() / clearInbox()} 三个端口，不关心接收链路细节。</p>
 */
class AlertInboxServiceTest {

    private AlertReceiverService receiver;
    private AlertInboxService service;

    private static final Instant BASE = Instant.parse("2026-10-20T10:00:00Z");

    private static AlertInboxEntry entry(String id, String status, String severity, Instant at) {
        return new AlertInboxEntry(id, at, status, "HighErrorRate", severity,
                "error rate > 1%", "10.0.0.1:8080", "2026-10-20T09:00:00Z", null);
    }

    @BeforeEach
    void setUp() {
        receiver = mock(AlertReceiverService.class);
        when(receiver.capacity()).thenReturn(200);
        service = new AlertInboxService(receiver);
    }

    @Test
    @DisplayName("列表：无筛选时按容量内全量返回，最新在前，字段映射正确（receivedAt→epochMillis）")
    void list_all_mapsFields() {
        when(receiver.inbox()).thenReturn(List.of(
                entry("b", "firing", "critical", BASE.plusSeconds(30)),
                entry("a", "firing", "critical", BASE)));

        List<AlertInboxItemView> views = service.list(null, null, null);

        assertEquals(2, views.size());
        assertEquals("b", views.get(0).id()); // 快照顺序即最新在前
        assertEquals(BASE.plusSeconds(30).toEpochMilli(), views.get(0).receivedAtEpochMillis());
        assertEquals("firing", views.get(0).status());
        assertEquals("HighErrorRate", views.get(0).name());
        assertEquals("critical", views.get(0).severity());
        assertEquals("error rate > 1%", views.get(0).summary());
        assertEquals("10.0.0.1:8080", views.get(0).instance());
        assertEquals("2026-10-20T09:00:00Z", views.get(0).startsAt());
    }

    @Test
    @DisplayName("列表：severity/status 筛选透传规则类（大小写不敏感），非法 status 忽略")
    void list_filtersDelegatedToRules() {
        when(receiver.inbox()).thenReturn(List.of(
                entry("a", "firing", "critical", BASE),
                entry("b", "firing", "warning", BASE),
                entry("c", "resolved", "critical", BASE)));

        assertEquals(List.of("a", "c"),
                service.list("CRITICAL", null, null).stream().map(AlertInboxItemView::id).toList());
        assertEquals(List.of("a", "b"),
                service.list(null, "firing", null).stream().map(AlertInboxItemView::id).toList());
        // 白名单外 status：容错忽略而非报错，等同不筛选
        assertEquals(3, service.list(null, "pending", null).size());
    }

    @Test
    @DisplayName("列表：limit 缺省取 100、越界钳 1、超过容量按容量自适应")
    void list_limitAdaptive() {
        when(receiver.capacity()).thenReturn(3);
        when(receiver.inbox()).thenReturn(List.of(
                entry("a", "firing", "critical", BASE),
                entry("b", "firing", "critical", BASE),
                entry("c", "firing", "critical", BASE),
                entry("d", "firing", "critical", BASE)));

        assertEquals(3, service.list(null, null, null).size());   // 默认 100 被容量钳到 3
        assertEquals(3, service.list(null, null, 999).size());    // 显式越界同样钳到容量
        assertEquals(1, service.list(null, null, 0).size());      // <1 钳 1
        assertEquals(2, service.list(null, null, 2).size());
    }

    @Test
    @DisplayName("列表：空收件箱返回空列表而非 null；summary 超长截断")
    void list_emptyAndTruncation() {
        when(receiver.inbox()).thenReturn(List.of());
        assertNotNull(service.list(null, null, null));
        assertTrue(service.list(null, null, null).isEmpty());

        String longSummary = "x".repeat(500);
        AlertInboxEntry e = new AlertInboxEntry("a", BASE, "firing", "n", "critical",
                longSummary, "i", null, null);
        when(receiver.inbox()).thenReturn(List.of(e));
        AlertInboxItemView view = service.list(null, null, null).get(0);
        assertEquals(200, view.summary().length());
    }

    @Test
    @DisplayName("汇总：规则类结果透传为视图（容量/分布/时间戳）")
    void stats_delegatesToRules() {
        when(receiver.inbox()).thenReturn(List.of(
                entry("a", "firing", "critical", BASE),
                entry("b", "resolved", "warning", BASE.plusSeconds(5))));

        AlertInboxStatsView stats = service.stats();

        assertEquals(200, stats.capacity());
        assertEquals(2, stats.total());
        assertEquals(1, stats.firingCount());
        assertEquals(1, stats.resolvedCount());
        assertEquals(2, stats.severityCounts().size());
        assertEquals(BASE.plusSeconds(5).toEpochMilli(), stats.mostRecentEpochMillis());
        assertEquals(BASE.toEpochMilli(), stats.oldestRetainedEpochMillis());
    }

    @Test
    @DisplayName("清空：委托接收服务并返回清理条数")
    void clear_delegates() {
        when(receiver.clearInbox()).thenReturn(7);
        assertEquals(7, service.clear());
        verify(receiver).clearInbox();
    }
}
