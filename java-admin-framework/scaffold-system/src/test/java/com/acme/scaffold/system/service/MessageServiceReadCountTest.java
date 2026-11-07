package com.acme.scaffold.system.service;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.realtime.service.RealtimePushService;
import com.acme.scaffold.system.entity.SysMessageReceiptDO;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 站内信已读计数回归测试（离线，jOOQ {@code MockConnection} 捕获渲染后的 SQL）。
 *
 * <p>锁定两条曾真实存在的数据不一致：</p>
 * <ol>
 *   <li>{@code markAllRead} 原先把 CASE 结果直接 SET 给 {@code read_count}，
 *       用本次批量回执数覆盖掉此前由 {@code markRead} 逐条累计的历史值；
 *       修复后必须为「历史值 + 本次增量」。</li>
 *   <li>{@code markRead} 原先「先查 is_read、再无条件 UPDATE」，并发/重放下会重复累加；
 *       修复后必须条件更新，抢不到状态跃迁时不再回写发件表。</li>
 * </ol>
 */
class MessageServiceReadCountTest {

    private static final long ME = 7L;

    private static SysMessageReceiptDO unreadReceipt(long id, long messageId) {
        SysMessageReceiptDO r = new SysMessageReceiptDO();
        r.setId(id);
        r.setMessageId(messageId);
        r.setUserId(ME);
        r.setIsRead(0);
        return r;
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(ME, "admin", "管理员", 0L, null, 1,
                Set.of("SUPER_ADMIN"), Set.of());
    }

    private static MessageService service(List<String> sqls, int[] updateRows) {
        MockDataProvider provider = ctx -> {
            sqls.add(ctx.sql());
            // 只有 UPDATE 需要返回受影响行数；SELECT 已被 JooqWriters 静态打桩拦截。
            int rows = ctx.sql().toLowerCase(Locale.ROOT).startsWith("update")
                    ? updateRows[0] : 0;
            return new MockResult[]{new MockResult(rows)};
        };
        DSLContext dsl = DSL.using(new MockConnection(provider), SQLDialect.MYSQL);
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        when(security.getCurrentPrincipal()).thenReturn(Optional.of(principal()));
        return new MessageService(dsl, security, mock(RealtimePushService.class));
    }

    /** 发件主表 sys_message 的 UPDATE（排除 sys_message_receipt，兼容引号渲染）。 */
    private static List<String> messageTableUpdates(List<String> sqls) {
        return sqls.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith("update"))
                .filter(s -> s.contains("sys_message") && !s.contains("sys_message_receipt"))
                .toList();
    }

    /** 回执表 sys_message_receipt 的 UPDATE。 */
    private static List<String> receiptTableUpdates(List<String> sqls) {
        return sqls.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith("update"))
                .filter(s -> s.contains("sys_message_receipt"))
                .toList();
    }

    @Test
    @DisplayName("markAllRead：read_count 必须按「历史值 + 本次增量」累加，而非覆盖")
    void markAllReadAccumulatesInsteadOfOverwriting() {
        List<String> sqls = new ArrayList<>();
        MessageService svc = service(sqls, new int[]{1});
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            writers.when(() -> JooqWriters.fetchList(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                            eq(SysMessageReceiptDO.class), any()))
                    .thenReturn(List.of(
                            unreadReceipt(1L, 101L),
                            unreadReceipt(2L, 101L),
                            unreadReceipt(3L, 102L)));
            assertEquals(3, svc.markAllRead());
        }

        List<String> msgUpdates = messageTableUpdates(sqls);
        assertEquals(1, msgUpdates.size(), "发件表应只有一条聚合 UPDATE，实际：" + msgUpdates);
        String sql = msgUpdates.get(0).toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("case"), "应按消息聚合本次回执增量：" + sql);
        assertTrue(sql.contains("coalesce"), "历史 read_count 可能为 null，须先归一：" + sql);
        assertTrue(sql.contains("+"), "必须是累加运算，而不是把 CASE 结果直接 SET 给列：" + sql);
    }

    @Test
    @DisplayName("markRead：抢到状态跃迁时仅累加一次，且累加带 null 归一")
    void markReadIncrementsOnceWhenUpdateWins() {
        List<String> sqls = new ArrayList<>();
        MessageService svc = service(sqls, new int[]{1});
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            writers.when(() -> JooqWriters.fetchOne(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                            eq(SysMessageReceiptDO.class), any()))
                    .thenReturn(unreadReceipt(1L, 101L));
            svc.markRead(101L);
        }

        List<String> receiptUpdates = receiptTableUpdates(sqls);
        assertEquals(1, receiptUpdates.size());
        String receiptSql = receiptUpdates.get(0).toLowerCase(Locale.ROOT);
        assertTrue(receiptSql.contains("coalesce"), "条件更新须兼容 is_read 为 null：" + receiptSql);
        assertTrue(receiptSql.contains("<>") || receiptSql.contains("!="),
                "UPDATE 必须带「仍未读」条件以保证并发幂等：" + receiptSql);

        List<String> msgUpdates = messageTableUpdates(sqls);
        assertEquals(1, msgUpdates.size(), "只有成功跃迁才回写发件表");
        assertTrue(msgUpdates.get(0).toLowerCase(Locale.ROOT).contains("coalesce"));
    }

    @Test
    @DisplayName("markRead：并发下未抢到状态跃迁（affected=0）时不再重复计数")
    void markReadSkipsIncrementWhenLostRace() {
        List<String> sqls = new ArrayList<>();
        MessageService svc = service(sqls, new int[]{0});
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            writers.when(() -> JooqWriters.fetchOne(any(), eq(JooqTables.SYS_MESSAGE_RECEIPT),
                            eq(SysMessageReceiptDO.class), any()))
                    .thenReturn(unreadReceipt(1L, 101L));
            svc.markRead(101L);
        }
        assertEquals(1, receiptTableUpdates(sqls).size());
        assertTrue(messageTableUpdates(sqls).isEmpty(),
                "affected=0 表示已被并发请求标记，绝不能再 +1");
    }
}
