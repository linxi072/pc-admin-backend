package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.system.dto.AnnouncementView;
import com.acme.scaffold.system.dto.MessageView;
import com.acme.scaffold.system.dto.NotificationItemView;
import com.acme.scaffold.system.dto.NotificationSummaryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NotificationCenterService} 单元测试：用 Mockito 替身两个依赖服务
 * （{@link AnnouncementReadService} 与 {@link MessageService}），断言聚合值与委托调用，
 * 不触及数据库。纯编排与映射逻辑，离线可跑。
 */
class NotificationCenterServiceTest {

    private final AnnouncementReadService announcementReadService = mock(AnnouncementReadService.class);
    private final MessageService messageService = mock(MessageService.class);
    private NotificationCenterService service;

    @BeforeEach
    void setUp() {
        service = new NotificationCenterService(announcementReadService, messageService);
    }

    private static AnnouncementView announcement(Long id, LocalDateTime publishAt) {
        // content 超长，用于验证后端摘要截断
        String longContent = "公告正文".repeat(40);
        return new AnnouncementView(id, "系统升级公告", longContent, "PUBLISHED", 0,
                publishAt, null, null, null, 1L, 3, true, 10, 4, publishAt.minusDays(1));
    }

    private static MessageView message(Long id, LocalDateTime sentAt) {
        String longContent = "站内信正文".repeat(40);
        return new MessageView(id, "您有一条新消息", longContent, "NOTICE", 1L, "张三", sentAt, false, null);
    }

    // ------------------------------------------------------------------
    // summary
    // ------------------------------------------------------------------

    @Test
    @DisplayName("summary：聚合公告未读 + 站内信未读 + 合计")
    void summary_aggregatesBothSources() {
        when(announcementReadService.countMyUnread()).thenReturn(3L);
        when(messageService.unreadCount()).thenReturn(5L);

        NotificationSummaryView summary = service.summary();

        assertEquals(3L, summary.announcementUnread());
        assertEquals(5L, summary.messageUnread());
        assertEquals(8L, summary.totalUnread());
        verify(announcementReadService).countMyUnread();
        verify(messageService).unreadCount();
    }

    @Test
    @DisplayName("summary：两来源均为 0 时合计为 0")
    void summary_zeroWhenNoUnread() {
        when(announcementReadService.countMyUnread()).thenReturn(0L);
        when(messageService.unreadCount()).thenReturn(0L);

        NotificationSummaryView summary = service.summary();

        assertEquals(0L, summary.totalUnread());
    }

    // ------------------------------------------------------------------
    // recent
    // ------------------------------------------------------------------

    @Test
    @DisplayName("recent：合并两类未读，按时间倒序，公告/站内信各自映射且摘要已截断")
    void recent_mergesAndSortsAndTruncatesSummary() {
        LocalDateTime now = LocalDateTime.now();
        when(announcementReadService.myUnread(anyInt(), anyInt()))
                .thenReturn(new PageResult<>(1, 10, 1L, List.of(announcement(11L, now.minusDays(1)))));
        when(messageService.pageMine(anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new PageResult<>(1, 10, 1L, List.of(message(21L, now.minusHours(2)))));

        List<NotificationItemView> items = service.recent(10);

        // 委托调用参数正确：各取前 10 条未读
        verify(announcementReadService).myUnread(1, 10);
        verify(messageService).pageMine(1, 10, true);

        assertEquals(2, items.size());
        // 最近发生的（站内信，2 小时前）应排第一
        assertEquals("MESSAGE", items.get(0).source());
        assertEquals("ANNOUNCEMENT", items.get(1).source());

        // 来源标识映射正确，跳转链接带 tab 区分
        assertTrue(items.get(0).link().contains("tab=message"));
        assertTrue(items.get(1).link().contains("tab=announcement"));

        // 长正文被后端截断为不超过 81 字符（80 + 省略号）
        for (NotificationItemView it : items) {
            assertTrue(it.summary() != null && it.summary().length() <= 81,
                    "摘要应被截断，实际长度=" + (it.summary() == null ? "null" : it.summary().length()));
            assertTrue(it.summary().endsWith("…"), "超长摘要应以省略号结尾");
        }
    }

    @Test
    @DisplayName("recent：limit 为 null 时按默认 10 委托")
    void recent_nullLimitUsesDefault() {
        when(announcementReadService.myUnread(anyInt(), anyInt()))
                .thenReturn(new PageResult<>(1, 10, 0L, List.of()));
        when(messageService.pageMine(anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new PageResult<>(1, 10, 0L, List.of()));

        service.recent(null);

        verify(announcementReadService).myUnread(1, 10);
        verify(messageService).pageMine(1, 10, true);
    }

    @Test
    @DisplayName("recent：某来源为空时仅合并另一来源")
    void recent_onlyOneSource() {
        when(announcementReadService.myUnread(anyInt(), anyInt()))
                .thenReturn(new PageResult<>(1, 10, 0L, List.of()));
        when(messageService.pageMine(anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new PageResult<>(1, 10, 1L, List.of(message(21L, LocalDateTime.now()))));

        List<NotificationItemView> items = service.recent(5);

        assertEquals(1, items.size());
        assertEquals("MESSAGE", items.get(0).source());
    }

    // ------------------------------------------------------------------
    // markAllRead
    // ------------------------------------------------------------------

    @Test
    @DisplayName("markAllRead：委托两类来源的 markAllRead 并返回合计")
    void markAllRead_delegatesAndSums() {
        when(announcementReadService.markAllRead()).thenReturn(2);
        when(messageService.markAllRead()).thenReturn(3);

        Map<String, Integer> result = service.markAllRead();

        assertEquals(2, result.get("announcementMarked"));
        assertEquals(3, result.get("messageMarked"));
        assertEquals(5, result.get("marked"));
        verify(announcementReadService, times(1)).markAllRead();
        verify(messageService, times(1)).markAllRead();
    }

    @Test
    @DisplayName("markAllRead：无未读时两类均返回 0")
    void markAllRead_none() {
        when(announcementReadService.markAllRead()).thenReturn(0);
        when(messageService.markAllRead()).thenReturn(0);

        Map<String, Integer> result = service.markAllRead();

        assertEquals(0, result.get("marked"));
    }
}
