package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.dto.AnnouncementReadStatsView;
import com.acme.scaffold.system.dto.AnnouncementReceiptQuery;
import com.acme.scaffold.system.dto.AnnouncementReceiptView;
import com.acme.scaffold.system.dto.AnnouncementView;
import com.acme.scaffold.system.dto.SendMessageRequest;
import com.acme.scaffold.system.entity.SysAnnouncementDO;
import com.acme.scaffold.system.entity.SysAnnouncementReadDO;
import com.acme.scaffold.system.entity.SysUserDO;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AnnouncementReadService} 单元测试：以 {@code mockStatic(JooqWriters)} 作为打库替身，
 * 覆盖懒插入回执的幂等语义（先查后插 + 唯一键兜底）、反连接 SQL 的相关性限定、
 * 统计推导与催办编排。不依赖数据库，离线可跑。
 *
 * <p>反连接相关性是本模块最易错的点（两表都有 id 列，无限定列名会被 MySQL 按内层
 * 作用域解析错），故对 myUnread / 未读用户 / 回执关键字三处条件做 SQL 渲染断言。
 */
class AnnouncementReadServiceTest {

    private static final long USER_ID = 7L;

    /** 仅用于条件构造的可渲染 DSLContext（无连接，只 build 不 execute）。 */
    private final DSLContext renderDsl = DSL.using(SQLDialect.MYSQL);
    private final DSLContext dsl = mock(DSLContext.class, RETURNS_DEEP_STUBS);
    private final SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
    private final MessageService messageService = mock(MessageService.class);

    private AnnouncementReadService service;

    @BeforeEach
    void setUp() {
        // 子查询（NOT EXISTS / IN）必须由可渲染的真实 DSL 构造，
        // 否则深桩 mock 渲染不出 SQL，反连接相关性断言失效
        when(dsl.selectOne()).thenAnswer(inv -> renderDsl.selectOne());
        when(dsl.select(any(SelectField.class)))
                .thenAnswer(inv -> renderDsl.select(inv.getArgument(0, SelectField.class)));
        when(securityContextFacade.getCurrentPrincipal())
                .thenReturn(Optional.of(new CurrentPrincipal(USER_ID, "tester", "测试用户",
                        0L, "client", 1, Set.of(), Set.of())));
        service = new AnnouncementReadService(dsl, securityContextFacade, messageService);
    }

    private static SysAnnouncementDO announcement(Long id, String status) {
        SysAnnouncementDO a = new SysAnnouncementDO();
        a.setId(id);
        a.setTitle("系统升级通知");
        a.setStatus(status);
        a.setIsTop(0);
        a.setViewCount(3);
        a.setAudienceCount(10);
        a.setReadCount(4);
        a.setPublishAt(LocalDateTime.now().minusDays(1));
        return a;
    }

    /** 渲染条件为 MySQL SQL（去反引号），用于断言关联限定与子查询形态。 */
    private static String render(JooqTables.TableRef table, Condition cond) {
        return DSL.using(SQLDialect.MYSQL).selectFrom(table.table()).where(cond).getSQL()
                .replace("`", "");
    }

    // ------------------------------------------------------------------
    // markRead
    // ------------------------------------------------------------------

    @Test
    @DisplayName("markRead：新回执插入并累加 read_count")
    void markRead_insertsReceiptAndIncrements() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_ANNOUNCEMENT),
                    eq(SysAnnouncementDO.class), eq(11L))).thenReturn(announcement(11L, "PUBLISHED"));
            mw.when(() -> JooqWriters.fetchOne(any(), eq(JooqTables.SYS_ANNOUNCEMENT_READ),
                    eq(SysAnnouncementReadDO.class), any())).thenReturn(null);

            service.markRead(11L);

            mw.verify(() -> JooqWriters.insert(any(), eq(JooqTables.SYS_ANNOUNCEMENT_READ),
                    any(SysAnnouncementReadDO.class)));
            // read_count 走原生 UPDATE +1
            verify(dsl).update(any(Table.class));
        }
    }

    @Test
    @DisplayName("markRead：已存在回执直接返回，不重复插入不重复计数")
    void markRead_existingReceiptIsIdempotent() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_ANNOUNCEMENT),
                    eq(SysAnnouncementDO.class), eq(11L))).thenReturn(announcement(11L, "PUBLISHED"));
            SysAnnouncementReadDO exist = new SysAnnouncementReadDO();
            exist.setId(99L);
            mw.when(() -> JooqWriters.fetchOne(any(), eq(JooqTables.SYS_ANNOUNCEMENT_READ),
                    eq(SysAnnouncementReadDO.class), any())).thenReturn(exist);

            assertDoesNotThrow(() -> service.markRead(11L));

            mw.verify(() -> JooqWriters.insert(any(), eq(JooqTables.SYS_ANNOUNCEMENT_READ),
                    any(SysAnnouncementReadDO.class)), Mockito.never());
            verify(dsl, never()).update(any(Table.class));
        }
    }

    @Test
    @DisplayName("markRead：唯一键并发冲突按已读处理，不抛错不计数")
    void markRead_uniqueViolationTreatedAsRead() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_ANNOUNCEMENT),
                    eq(SysAnnouncementDO.class), eq(11L))).thenReturn(announcement(11L, "PUBLISHED"));
            mw.when(() -> JooqWriters.fetchOne(any(), eq(JooqTables.SYS_ANNOUNCEMENT_READ),
                    eq(SysAnnouncementReadDO.class), any())).thenReturn(null);
            mw.when(() -> JooqWriters.insert(any(), eq(JooqTables.SYS_ANNOUNCEMENT_READ),
                    any(SysAnnouncementReadDO.class)))
                    .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));

            assertDoesNotThrow(() -> service.markRead(11L));

            verify(dsl, never()).update(any(Table.class));
        }
    }

    @Test
    @DisplayName("markRead：公告不存在抛 NOT_FOUND")
    void markRead_notFound() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_ANNOUNCEMENT),
                    eq(SysAnnouncementDO.class), eq(404L))).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.markRead(404L));
            assertTrue(ex.getMessage().contains("公告不存在"));
        }
    }

    @Test
    @DisplayName("markRead：未发布公告拒绝标记已读")
    void markRead_rejectsNonPublished() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_ANNOUNCEMENT),
                    eq(SysAnnouncementDO.class), eq(11L))).thenReturn(announcement(11L, "DRAFT"));
            BusinessException ex = assertThrows(BusinessException.class, () -> service.markRead(11L));
            assertTrue(ex.getMessage().contains("未发布"));
        }
    }

    // ------------------------------------------------------------------
    // myUnread（反连接）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("myUnread：分页结果映射为视图，条件含 NOT EXISTS 且外表列已限定")
    void myUnread_mapsViewsAndAntiJoinQualified() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            SysAnnouncementDO a = announcement(11L, "PUBLISHED");
            mw.when(() -> JooqWriters.page(any(), eq(JooqTables.SYS_ANNOUNCEMENT),
                    eq(SysAnnouncementDO.class), any(), any(com.acme.scaffold.common.api.dto.PageQuery.class),
                    any(org.jooq.OrderField.class), any(org.jooq.OrderField.class), any(org.jooq.OrderField.class)))
                    .thenReturn(new PageResult<>(1, 20, 1L, List.of(a)));

            PageResult<AnnouncementView> result = service.myUnread(1, 20);

            assertEquals(1, result.total());
            AnnouncementView view = result.records().get(0);
            assertEquals(11L, view.id());
            assertTrue(view.effective());
            assertEquals(10, view.audienceCount());
            assertEquals(4, view.readCount());

            // 反连接相关性断言：NOT EXISTS 子查询内外列均带表名限定
            ArgumentCaptor<Condition> captor = ArgumentCaptor.forClass(Condition.class);
            mw.verify(() -> JooqWriters.page(any(), eq(JooqTables.SYS_ANNOUNCEMENT),
                    eq(SysAnnouncementDO.class), captor.capture(), any(), any(org.jooq.OrderField.class),
                    any(org.jooq.OrderField.class), any(org.jooq.OrderField.class)));
            String sql = render(JooqTables.SYS_ANNOUNCEMENT, captor.getValue()).toLowerCase();
            assertTrue(sql.contains("not exists"), "应含 NOT EXISTS 反连接: " + sql);
            assertTrue(sql.contains("sys_announcement_read.announcement_id = sys_announcement.id"),
                    "子查询两侧列必须带表名限定，否则 MySQL 按内层作用域把 id 解析成回执表 id: " + sql);
            assertTrue(sql.contains("sys_announcement_read.user_id"), sql);
        }
    }

    // ------------------------------------------------------------------
    // pageReceipts
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pageReceipts：批量装载显示名，用户缺失时降级为 null")
    void pageReceipts_mapsDisplayNamesWithFallback() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            SysAnnouncementReadDO r1 = new SysAnnouncementReadDO();
            r1.setId(1L); r1.setAnnouncementId(11L); r1.setUserId(101L);
            r1.setReadAt(LocalDateTime.now());
            SysAnnouncementReadDO r2 = new SysAnnouncementReadDO();
            r2.setId(2L); r2.setAnnouncementId(11L); r2.setUserId(102L);
            r2.setReadAt(LocalDateTime.now());
            mw.when(() -> JooqWriters.page(any(), eq(JooqTables.SYS_ANNOUNCEMENT_READ),
                    eq(SysAnnouncementReadDO.class), any(), any(com.acme.scaffold.common.api.dto.PageQuery.class),
                    any(org.jooq.OrderField.class), any(org.jooq.OrderField.class)))
                    .thenReturn(new PageResult<>(1, 20, 2L, List.of(r1, r2)));
            SysUserDO u = new SysUserDO();
            u.setId(101L); u.setDisplayName("张三");
            mw.when(() -> JooqWriters.fetchList(any(), eq(JooqTables.SYS_USER),
                    eq(SysUserDO.class), any())).thenReturn(List.of(u));

            PageResult<AnnouncementReceiptView> result =
                    service.pageReceipts(new AnnouncementReceiptQuery(11L, 1, 20, null));

            assertEquals(2, result.total());
            assertEquals("张三", result.records().get(0).displayName());
            assertNull(result.records().get(1).displayName()); // 用户已删 → 缺项降级
            assertEquals(11L, result.records().get(0).announcementId());
        }
    }

    @Test
    @DisplayName("pageReceipts：阅读人关键字收敛为 user_id IN 子查询（主表单表分页免 join）")
    void pageReceipts_keywordBecomesInSubquery() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.page(any(), eq(JooqTables.SYS_ANNOUNCEMENT_READ),
                    eq(SysAnnouncementReadDO.class), any(), any(com.acme.scaffold.common.api.dto.PageQuery.class),
                    any(org.jooq.OrderField.class), any(org.jooq.OrderField.class)))
                    .thenReturn(new PageResult<>(1, 20, 0L, List.of()));

            service.pageReceipts(new AnnouncementReceiptQuery(11L, 1, 20, "张三"));

            ArgumentCaptor<Condition> captor = ArgumentCaptor.forClass(Condition.class);
            mw.verify(() -> JooqWriters.page(any(), eq(JooqTables.SYS_ANNOUNCEMENT_READ),
                    eq(SysAnnouncementReadDO.class), captor.capture(), any(), any(org.jooq.OrderField.class),
                    any(org.jooq.OrderField.class)));
            String sql = render(JooqTables.SYS_ANNOUNCEMENT_READ, captor.getValue()).toLowerCase();
            assertTrue(sql.contains("user_id in"), sql);
            assertTrue(sql.contains("select"), sql);
            assertTrue(sql.contains("display_name like"), sql);
            assertTrue(sql.contains("username like"), sql);
        }
    }

    // ------------------------------------------------------------------
    // stats / listStats
    // ------------------------------------------------------------------

    @Test
    @DisplayName("stats：分子分母与推导值（未读数/已读率）一次齐备")
    void stats_derivesUnreadAndRate() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_ANNOUNCEMENT),
                    eq(SysAnnouncementDO.class), eq(11L))).thenReturn(announcement(11L, "PUBLISHED"));
            AnnouncementReadStatsView stats = service.stats(11L);
            assertEquals(10, stats.audienceCount());
            assertEquals(4, stats.readCount());
            assertEquals(6, stats.unreadCount());
            assertEquals(40.0, stats.readRate());
        }
    }

    @Test
    @DisplayName("stats：公告不存在抛 NOT_FOUND")
    void stats_notFound() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_ANNOUNCEMENT),
                    eq(SysAnnouncementDO.class), eq(404L))).thenReturn(null);
            assertThrows(BusinessException.class, () -> service.stats(404L));
        }
    }

    @Test
    @DisplayName("listStats：分页映射为统计视图")
    void listStats_mapsStatsViews() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.page(any(), eq(JooqTables.SYS_ANNOUNCEMENT),
                    eq(SysAnnouncementDO.class), any(), any(com.acme.scaffold.common.api.dto.PageQuery.class),
                    any(org.jooq.OrderField.class), any(org.jooq.OrderField.class), any(org.jooq.OrderField.class)))
                    .thenReturn(new PageResult<>(1, 20, 1L, List.of(announcement(11L, "PUBLISHED"))));

            PageResult<AnnouncementReadStatsView> result = service.listStats(1, 20, null);

            assertEquals(1, result.total());
            assertEquals(40.0, result.records().get(0).readRate());
            assertEquals(6, result.records().get(0).unreadCount());
        }
    }

    // ------------------------------------------------------------------
    // remindUnread
    // ------------------------------------------------------------------

    @Test
    @DisplayName("remindUnread：未读用户经 MessageService.doSend 投递并返回人数")
    void remindUnread_sendsViaDoSend() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_ANNOUNCEMENT),
                    eq(SysAnnouncementDO.class), eq(11L))).thenReturn(announcement(11L, "PUBLISHED"));
            SysUserDO u1 = new SysUserDO(); u1.setId(101L);
            SysUserDO u2 = new SysUserDO(); u2.setId(102L);
            mw.when(() -> JooqWriters.fetchTop(any(), eq(JooqTables.SYS_USER),
                    eq(SysUserDO.class), any(), eq(100), any(org.jooq.OrderField.class)))
                    .thenReturn(List.of(u1, u2));
            when(messageService.doSend(any(SendMessageRequest.class))).thenReturn(2);

            int sent = service.remindUnread(11L, null);

            assertEquals(2, sent);
            ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
            verify(messageService).doSend(captor.capture());
            SendMessageRequest req = captor.getValue();
            assertEquals(List.of(101L, 102L), req.receiverIds());
            assertEquals("NOTICE", req.msgType());
            assertNotNull(req.title());
            assertTrue(req.title().startsWith("公告催办："));
        }
    }

    @Test
    @DisplayName("remindUnread：批量上限收敛到 MAX_REMIND_LIMIT")
    void remindUnread_clampsLimit() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_ANNOUNCEMENT),
                    eq(SysAnnouncementDO.class), eq(11L))).thenReturn(announcement(11L, "PUBLISHED"));
            mw.when(() -> JooqWriters.fetchTop(any(), eq(JooqTables.SYS_USER),
                    eq(SysUserDO.class), any(), eq(500), any(org.jooq.OrderField.class)))
                    .thenReturn(List.of());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.remindUnread(11L, 9999));
            assertTrue(ex.getMessage().contains("没有未读用户"));
            // 9999 被收敛为 500 后仍未读到人 → 冲突（证明 limit 已钳制）
            mw.verify(() -> JooqWriters.fetchTop(any(), eq(JooqTables.SYS_USER),
                    eq(SysUserDO.class), any(), eq(500), any(org.jooq.OrderField.class)));
        }
    }

    @Test
    @DisplayName("remindUnread：非已发布公告拒绝催办")
    void remindUnread_rejectsNonPublished() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_ANNOUNCEMENT),
                    eq(SysAnnouncementDO.class), eq(11L))).thenReturn(announcement(11L, "OFFLINE"));
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.remindUnread(11L, 10));
            assertTrue(ex.getMessage().contains("仅已发布的公告可以催办"));
            verify(messageService, never()).doSend(any());
        }
    }

    @Test
    @DisplayName("remindUnread：未读用户查询条件为带表名限定的 NOT EXISTS")
    void remindUnread_antiJoinQualified() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_ANNOUNCEMENT),
                    eq(SysAnnouncementDO.class), eq(11L))).thenReturn(announcement(11L, "PUBLISHED"));
            mw.when(() -> JooqWriters.fetchTop(any(), eq(JooqTables.SYS_USER),
                    eq(SysUserDO.class), any(), anyInt(), any(org.jooq.OrderField.class)))
                    .thenReturn(List.of());

            assertThrows(BusinessException.class, () -> service.remindUnread(11L, 10));

            ArgumentCaptor<Condition> captor = ArgumentCaptor.forClass(Condition.class);
            mw.verify(() -> JooqWriters.fetchTop(any(), eq(JooqTables.SYS_USER),
                    eq(SysUserDO.class), captor.capture(), anyInt(), any(org.jooq.OrderField.class)));
            String sql = render(JooqTables.SYS_USER, captor.getValue()).toLowerCase();
            assertTrue(sql.contains("not exists"), sql);
            assertTrue(sql.contains("sys_announcement_read.user_id = sys_user.id"),
                    "子查询引用 sys_user.id 必须限定，否则被解析成回执表 id: " + sql);
        }
    }
}
