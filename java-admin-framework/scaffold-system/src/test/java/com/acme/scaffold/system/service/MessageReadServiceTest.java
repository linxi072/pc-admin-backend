package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.dto.MessageReadStatsView;
import com.acme.scaffold.system.dto.MessageReceiptQuery;
import com.acme.scaffold.system.dto.MessageReceiptView;
import com.acme.scaffold.system.dto.SendMessageRequest;
import com.acme.scaffold.system.entity.SysMessageDO;
import com.acme.scaffold.system.entity.SysMessageReceiptDO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * {@link MessageReadService} 单元测试：以 {@code mockStatic(JooqWriters)} 作为打库替身，
 * 覆盖 eager 投递模型的已读统计聚合（分母=回执行数、已读= is_read=1 行数）、回执分页
 * （关键字→user_id IN 子查询、状态筛选、显示名批量装载）、催办编排（未读经 doSend 投递）。
 * 不依赖数据库，离线可跑。
 *
 * <p>与公告的懒插入模型不同，站内信无反连接，故测试重点转向：统计聚合的两个 count 条件形态、
 * 关键字/状态筛选的条件下推、以及「仅发送者可查看/催办」的归属校验。
 */
class MessageReadServiceTest {

    private static final long USER_ID = 7L;
    private static final long OTHER_USER = 99L;

    /** 仅用于条件构造的可渲染 DSLContext（无连接，只 build 不 execute）。 */
    private final DSLContext renderDsl = DSL.using(SQLDialect.MYSQL);
    private final DSLContext dsl = mock(DSLContext.class, RETURNS_DEEP_STUBS);
    private final SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
    private final MessageService messageService = mock(MessageService.class);

    private MessageReadService service;

    @BeforeEach
    void setUp() {
        // 子查询（IN）必须由可渲染的真实 DSL 构造，否则深桩 mock 渲染不出 SQL
        when(dsl.selectOne()).thenAnswer(inv -> renderDsl.selectOne());
        when(dsl.select(any(SelectField.class)))
                .thenAnswer(inv -> renderDsl.select(inv.getArgument(0, SelectField.class)));
        when(securityContextFacade.getCurrentPrincipal())
                .thenReturn(Optional.of(new CurrentPrincipal(USER_ID, "tester", "测试用户",
                        0L, "client", 1, Set.of(), Set.of())));
        service = new MessageReadService(dsl, securityContextFacade, messageService);
    }

    private static SysMessageDO ownMessage(Long id, Long senderId) {
        SysMessageDO m = new SysMessageDO();
        m.setId(id);
        m.setTitle("版本发布通知");
        m.setMsgType("NOTICE");
        m.setSenderId(senderId);
        m.setTotalCount(10);
        m.setReadCount(4);
        m.setSentAt(LocalDateTime.now().minusDays(1));
        return m;
    }

    private static SysUserDO user(Long id, String name) {
        SysUserDO u = new SysUserDO();
        u.setId(id);
        u.setDisplayName(name);
        u.setUsername("u" + id);
        return u;
    }

    /** 渲染条件为 MySQL SQL（去反引号），用于断言子查询形态与限定列。 */
    private static String render(JooqTables.TableRef table, Condition cond) {
        return DSL.using(SQLDialect.MYSQL).selectFrom(table.table()).where(cond).getSQL()
                .replace("`", "");
    }

    // ------------------------------------------------------------------
    // readStats（eager 聚合）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("readStats：从回执表聚合出分子分母与推导值（未读数/已读率）")
    void readStats_aggregatesFromReceiptTable() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(11L))).thenReturn(ownMessage(11L, USER_ID));
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_USER),
                    eq(SysUserDO.class), eq(USER_ID))).thenReturn(user(USER_ID, "管理员"));
            // 第一次 count = 总数(10)，第二次 count = 已读(4)
            mw.when(() -> JooqWriters.count(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT), any()))
                    .thenReturn(10L, 4L);

            MessageReadStatsView stats = service.readStats(11L);

            assertEquals(11L, stats.messageId());
            assertEquals(10, stats.totalCount());
            assertEquals(4, stats.readCount());
            assertEquals(6, stats.unreadCount());
            assertEquals(40.0, stats.readRate());
            assertEquals("管理员", stats.senderName());
        }
    }

    @Test
    @DisplayName("readStats：两个 count 条件均按 message_id 过滤，已读 count 额外限定 is_read = 1")
    void readStats_countConditionsQualified() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(11L))).thenReturn(ownMessage(11L, USER_ID));
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_USER),
                    eq(SysUserDO.class), eq(USER_ID))).thenReturn(user(USER_ID, "管理员"));
            mw.when(() -> JooqWriters.count(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT), any()))
                    .thenReturn(10L, 4L);

            service.readStats(11L);

            ArgumentCaptor<Condition> captor = ArgumentCaptor.forClass(Condition.class);
            mw.verify(() -> JooqWriters.count(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                    captor.capture()), Mockito.times(2));
            List<Condition> conds = captor.getAllValues();
            // jOOQ 把字面量渲染为绑定参数 '?'，且 TableRef.field 产出无限定列名，
            // 故断言形态为 message_id = ? 与 is_read = ?（不比对具体值）
            boolean allHaveMessageId = conds.stream()
                    .map(c -> render(JooqTables.SYS_MESSAGE_RECEIPT, c).toLowerCase())
                    .allMatch(s -> s.contains("message_id ="));
            assertTrue(allHaveMessageId, "两个 count 都必须按 message_id 过滤: " + conds);
            long withIsRead = conds.stream()
                    .map(c -> render(JooqTables.SYS_MESSAGE_RECEIPT, c).toLowerCase())
                    .filter(s -> s.contains("is_read =")).count();
            assertEquals(1, withIsRead, "恰好一个 count 限定已读 is_read = ?: " + conds);
        }
    }

    @Test
    @DisplayName("readStats：消息不存在抛 NOT_FOUND")
    void readStats_notFound() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(404L))).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.readStats(404L));
            assertTrue(ex.getMessage().contains("站内信不存在"));
        }
    }

    @Test
    @DisplayName("readStats：非发送者查看他人消息抛 FORBIDDEN")
    void readStats_forbiddenWhenNotOwner() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(11L))).thenReturn(ownMessage(11L, OTHER_USER));
            BusinessException ex = assertThrows(BusinessException.class, () -> service.readStats(11L));
            assertTrue(ex.getMessage().contains("只能查看自己发出的站内信"));
        }
    }

    // ------------------------------------------------------------------
    // pageReceipts
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pageReceipts：批量装载显示名，用户缺失时降级为 null")
    void pageReceipts_mapsDisplayNamesWithFallback() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(11L))).thenReturn(ownMessage(11L, USER_ID));
            SysMessageReceiptDO r1 = new SysMessageReceiptDO();
            r1.setId(1L); r1.setMessageId(11L); r1.setUserId(101L);
            r1.setIsRead(1); r1.setReadAt(LocalDateTime.now());
            SysMessageReceiptDO r2 = new SysMessageReceiptDO();
            r2.setId(2L); r2.setMessageId(11L); r2.setUserId(102L);
            r2.setIsRead(0);
            mw.when(() -> JooqWriters.page(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                    eq(SysMessageReceiptDO.class), any(), any(com.acme.scaffold.common.api.dto.PageQuery.class),
                    any(org.jooq.OrderField.class), any(org.jooq.OrderField.class)))
                    .thenReturn(new PageResult<>(1, 20, 2L, List.of(r1, r2)));
            SysUserDO u = new SysUserDO();
            u.setId(101L); u.setDisplayName("张三");
            mw.when(() -> JooqWriters.fetchList(any(), eq(JooqTables.SYS_USER),
                    eq(SysUserDO.class), any())).thenReturn(List.of(u));

            PageResult<MessageReceiptView> result =
                    service.pageReceipts(new MessageReceiptQuery(11L, 1, 20, null, null));

            assertEquals(2, result.total());
            MessageReceiptView v0 = result.records().get(0);
            assertEquals("张三", v0.displayName());
            assertTrue(v0.isRead());
            assertNotNull(v0.readAt());
            MessageReceiptView v1 = result.records().get(1);
            assertNull(v1.displayName()); // 用户已删 → 缺项降级
            assertFalse(v1.isRead());
            assertNull(v1.readAt());       // 未读 readAt 为 null
            assertEquals(11L, v0.messageId());
        }
    }

    @Test
    @DisplayName("pageReceipts：阅读人关键字收敛为 user_id IN 子查询（主表单表分页免 join）")
    void pageReceipts_keywordBecomesInSubquery() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(11L))).thenReturn(ownMessage(11L, USER_ID));
            mw.when(() -> JooqWriters.page(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                    eq(SysMessageReceiptDO.class), any(), any(com.acme.scaffold.common.api.dto.PageQuery.class),
                    any(org.jooq.OrderField.class), any(org.jooq.OrderField.class)))
                    .thenReturn(new PageResult<>(1, 20, 0L, List.of()));

            service.pageReceipts(new MessageReceiptQuery(11L, 1, 20, "张三", null));

            ArgumentCaptor<Condition> captor = ArgumentCaptor.forClass(Condition.class);
            mw.verify(() -> JooqWriters.page(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                    eq(SysMessageReceiptDO.class), captor.capture(), any(), any(org.jooq.OrderField.class),
                    any(org.jooq.OrderField.class)));
            String sql = render(JooqTables.SYS_MESSAGE_RECEIPT, captor.getValue()).toLowerCase();
            assertTrue(sql.contains("user_id in"), sql);
            assertTrue(sql.contains("select"), sql);
            assertTrue(sql.contains("display_name like"), sql);
            assertTrue(sql.contains("username like"), sql);
        }
    }

    @Test
    @DisplayName("pageReceipts：READ 状态筛选下推为 is_read 等值")
    void pageReceipts_readStatusRead() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(11L))).thenReturn(ownMessage(11L, USER_ID));
            mw.when(() -> JooqWriters.page(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                    eq(SysMessageReceiptDO.class), any(), any(com.acme.scaffold.common.api.dto.PageQuery.class),
                    any(org.jooq.OrderField.class), any(org.jooq.OrderField.class)))
                    .thenReturn(new PageResult<>(1, 20, 0L, List.of()));

            service.pageReceipts(new MessageReceiptQuery(11L, 1, 20, null, "READ"));
            ArgumentCaptor<Condition> readCaptor = ArgumentCaptor.forClass(Condition.class);
            mw.verify(() -> JooqWriters.page(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                    eq(SysMessageReceiptDO.class), readCaptor.capture(), any(),
                    any(org.jooq.OrderField.class), any(org.jooq.OrderField.class)));
            String readSql = render(JooqTables.SYS_MESSAGE_RECEIPT, readCaptor.getValue()).toLowerCase();
            assertTrue(readSql.contains("message_id ="), readSql);
            assertTrue(readSql.contains("is_read ="), readSql);
        }
    }

    @Test
    @DisplayName("pageReceipts：UNREAD 状态筛选同样下推为 is_read 等值")
    void pageReceipts_readStatusUnread() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(11L))).thenReturn(ownMessage(11L, USER_ID));
            mw.when(() -> JooqWriters.page(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                    eq(SysMessageReceiptDO.class), any(), any(com.acme.scaffold.common.api.dto.PageQuery.class),
                    any(org.jooq.OrderField.class), any(org.jooq.OrderField.class)))
                    .thenReturn(new PageResult<>(1, 20, 0L, List.of()));

            service.pageReceipts(new MessageReceiptQuery(11L, 1, 20, null, "UNREAD"));
            ArgumentCaptor<Condition> unreadCaptor = ArgumentCaptor.forClass(Condition.class);
            mw.verify(() -> JooqWriters.page(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                    eq(SysMessageReceiptDO.class), unreadCaptor.capture(), any(),
                    any(org.jooq.OrderField.class), any(org.jooq.OrderField.class)));
            String unreadSql = render(JooqTables.SYS_MESSAGE_RECEIPT, unreadCaptor.getValue()).toLowerCase();
            assertTrue(unreadSql.contains("message_id ="), unreadSql);
            assertTrue(unreadSql.contains("is_read ="), unreadSql);
        }
    }

    @Test
    @DisplayName("pageReceipts：非发送者查看他人消息回执抛 FORBIDDEN")
    void pageReceipts_forbiddenWhenNotOwner() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(11L))).thenReturn(ownMessage(11L, OTHER_USER));
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.pageReceipts(new MessageReceiptQuery(11L, 1, 20, null, null)));
            assertTrue(ex.getMessage().contains("只能查看自己发出的站内信"));
        }
    }

    // ------------------------------------------------------------------
    // remindUnread
    // ------------------------------------------------------------------

    @Test
    @DisplayName("remindUnread：未读用户经 MessageService.doSend 投递并返回人数")
    void remindUnread_sendsViaDoSend() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(11L))).thenReturn(ownMessage(11L, USER_ID));
            SysMessageReceiptDO u1 = new SysMessageReceiptDO(); u1.setUserId(101L);
            SysMessageReceiptDO u2 = new SysMessageReceiptDO(); u2.setUserId(102L);
            mw.when(() -> JooqWriters.fetchTop(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                    eq(SysMessageReceiptDO.class), any(), anyInt(), any(org.jooq.OrderField.class)))
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
            assertTrue(req.title().startsWith("站内信催办："));
        }
    }

    @Test
    @DisplayName("remindUnread：无未读用户抛 CONFLICT，不触发 doSend")
    void remindUnread_emptyThrowsConflict() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(11L))).thenReturn(ownMessage(11L, USER_ID));
            mw.when(() -> JooqWriters.fetchTop(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                    eq(SysMessageReceiptDO.class), any(), anyInt(), any(org.jooq.OrderField.class)))
                    .thenReturn(List.of());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.remindUnread(11L, null));
            assertTrue(ex.getMessage().contains("没有未读用户"));
            verify(messageService, never()).doSend(any());
        }
    }

    @Test
    @DisplayName("remindUnread：批量上限收敛到 MAX_REMIND_LIMIT（9999→500）")
    void remindUnread_clampsLimit() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(11L))).thenReturn(ownMessage(11L, USER_ID));
            mw.when(() -> JooqWriters.fetchTop(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                    eq(SysMessageReceiptDO.class), any(), eq(500), any(org.jooq.OrderField.class)))
                    .thenReturn(List.of());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.remindUnread(11L, 9999));
            assertTrue(ex.getMessage().contains("没有未读用户"));
            // 9999 被收敛为 500 后仍未读到人 → 冲突（证明 limit 已钳制）
            mw.verify(() -> JooqWriters.fetchTop(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                    eq(SysMessageReceiptDO.class), any(), eq(500), any(org.jooq.OrderField.class)));
        }
    }

    @Test
    @DisplayName("remindUnread：非发送者催办他人消息抛 FORBIDDEN")
    void remindUnread_forbiddenWhenNotOwner() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(11L))).thenReturn(ownMessage(11L, OTHER_USER));
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.remindUnread(11L, 10));
            assertTrue(ex.getMessage().contains("只能查看自己发出的站内信"));
            verify(messageService, never()).doSend(any());
        }
    }

    @Test
    @DisplayName("remindUnread：消息不存在抛 NOT_FOUND")
    void remindUnread_notFound() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(404L))).thenReturn(null);
            assertThrows(BusinessException.class, () -> service.remindUnread(404L, null));
        }
    }

    @Test
    @DisplayName("提醒：归聚合规——未读查询条件按 is_read = 0 过滤")
    void remindUnread_unreadConditionQualified() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_MESSAGE),
                    eq(SysMessageDO.class), eq(11L))).thenReturn(ownMessage(11L, USER_ID));
            mw.when(() -> JooqWriters.fetchTop(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                    eq(SysMessageReceiptDO.class), any(), anyInt(), any(org.jooq.OrderField.class)))
                    .thenReturn(List.of());
            assertDoesNotThrow(() -> {
                try {
                    service.remindUnread(11L, 10);
                } catch (BusinessException ignored) {
                    // 没有未读用户属于预期分支，此处只验证条件形态
                }
            });
            ArgumentCaptor<Condition> captor = ArgumentCaptor.forClass(Condition.class);
            mw.verify(() -> JooqWriters.fetchTop(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                    eq(SysMessageReceiptDO.class), captor.capture(), anyInt(), any(org.jooq.OrderField.class)));
            String sql = render(JooqTables.SYS_MESSAGE_RECEIPT, captor.getValue()).toLowerCase();
            assertTrue(sql.contains("message_id ="), sql);
            assertTrue(sql.contains("is_read ="), sql);
        }
    }
}
