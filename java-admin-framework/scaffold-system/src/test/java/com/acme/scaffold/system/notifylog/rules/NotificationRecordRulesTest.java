package com.acme.scaffold.system.notifylog.rules;

import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NotificationRecordRules} 离线单测：筛选值归一、摘要、按渠道接收方掩码与计数。
 *
 * <p>全部为纯函数断言，不依赖 Spring 上下文与数据库；掩码口径与
 * {@code SensitiveMasker} / {@code SmsRecordRules} 保持一致是重点守护项。</p>
 */
class NotificationRecordRulesTest {

    // ------------------------------------------------------------------
    // 筛选值归一
    // ------------------------------------------------------------------

    @Test
    @DisplayName("normalizeChannel：枚举名大小写不敏感，未知/空白返回 null")
    void normalizeChannel() {
        assertEquals("EMAIL", NotificationRecordRules.normalizeChannel("email"));
        assertEquals("IN_APP", NotificationRecordRules.normalizeChannel(" In_App "));
        assertNull(NotificationRecordRules.normalizeChannel("DINGTALK"));
        assertNull(NotificationRecordRules.normalizeChannel("  "));
        assertNull(NotificationRecordRules.normalizeChannel(null));
    }

    @Test
    @DisplayName("normalizeStatus：仅接受 SUCCESS/FAILED/SKIPPED/DIGEST_PENDING，其余返回 null")
    void normalizeStatus() {
        assertEquals("SUCCESS", NotificationRecordRules.normalizeStatus("success"));
        assertEquals("FAILED", NotificationRecordRules.normalizeStatus(" Failed "));
        assertEquals("SKIPPED", NotificationRecordRules.normalizeStatus("skipped"));
        assertEquals("DIGEST_PENDING", NotificationRecordRules.normalizeStatus("digest_pending"));
        assertNull(NotificationRecordRules.normalizeStatus("PENDING"));
        assertNull(NotificationRecordRules.normalizeStatus(null));
    }

    // ------------------------------------------------------------------
    // 摘要
    // ------------------------------------------------------------------

    @Test
    @DisplayName("summarizeTitle/Content：空白为空串，控制符折叠，超长截断加省略号")
    void summarize() {
        assertEquals("", NotificationRecordRules.summarizeTitle("   "));
        assertEquals("", NotificationRecordRules.summarizeContent(null));
        assertEquals("a b c", NotificationRecordRules.summarizeTitle("a\r\nb\t c"));

        String longTitle = "标".repeat(300);
        String summarized = NotificationRecordRules.summarizeTitle(longTitle);
        assertEquals(NotificationRecordRules.MAX_TITLE_LEN + 1, summarized.length());
        assertTrue(summarized.endsWith("…"));
    }

    @Test
    @DisplayName("summarizeExtra：{k=v} 形态，键数封顶，空 map 为空串")
    void summarizeExtra() {
        assertEquals("", NotificationRecordRules.summarizeExtra(null));
        assertEquals("", NotificationRecordRules.summarizeExtra(Map.of()));
        assertEquals("{a=1}", NotificationRecordRules.summarizeExtra(Map.of("a", 1)));

        Map<String, Object> many = Map.of("a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7);
        String out = NotificationRecordRules.summarizeExtra(many);
        assertTrue(out.startsWith("{"));
        assertTrue(out.contains("…共7项"), out);
    }

    // ------------------------------------------------------------------
    // 掩码
    // ------------------------------------------------------------------

    @Test
    @DisplayName("maskMobiles/maskEmails：保序拼接，非形态原样，空列表为空串")
    void maskLists() {
        assertEquals("138****8000,139****9000",
                NotificationRecordRules.maskMobiles(List.of(" 13800008000 ", "13900009000")));
        assertEquals("", NotificationRecordRules.maskMobiles(List.of()));
        assertEquals("", NotificationRecordRules.maskMobiles(null));

        assertEquals("z***@example.com,a***@test.cn",
                NotificationRecordRules.maskEmails(List.of("zhang@example.com", "anna@test.cn")));
        assertEquals("", NotificationRecordRules.maskEmails(null));
    }

    @Test
    @DisplayName("maskWebhookUrl：query 串整体抹除，path 保留")
    void maskWebhookUrl() {
        assertEquals("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?***",
                NotificationRecordRules.maskWebhookUrl("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abc123"));
        assertEquals("https://example.com/hook", NotificationRecordRules.maskWebhookUrl("https://example.com/hook"));
        assertEquals("", NotificationRecordRules.maskWebhookUrl(" "));
    }

    @Test
    @DisplayName("idsSummary：前 5 条展示后折叠为「…共N个」")
    void idsSummary() {
        assertEquals("1,2,3", NotificationRecordRules.idsSummary(List.of(1L, 2L, 3L)));
        assertEquals("1,2,3,4,5,…共8个",
                NotificationRecordRules.idsSummary(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)));
        assertEquals("", NotificationRecordRules.idsSummary(null));
    }

    // ------------------------------------------------------------------
    // 按渠道接收方摘要与计数
    // ------------------------------------------------------------------

    private static NotificationMessage msg(Channel ch, List<Long> receiverIds, List<Long> roleIds,
                                           List<Long> orgIds, String webhookUrl,
                                           Map<String, Object> extra, List<String> emails,
                                           List<String> mobiles) {
        return new NotificationMessage(ch, null, null, null, receiverIds, roleIds, orgIds,
                webhookUrl, extra, emails, mobiles);
    }

    @Test
    @DisplayName("receiverSummary：SMS/EMAIL 掩码列表，WEBHOOK 抹 query，WEBSOCKET 区分定向/广播")
    void receiverSummary_channels() {
        assertEquals("138****8000,139****9000",
                NotificationRecordRules.receiverSummary(
                        msg(Channel.SMS, null, null, null, null, null, null,
                                List.of("13800008000", "13900009000"))));
        assertEquals("z***@example.com",
                NotificationRecordRules.receiverSummary(
                        msg(Channel.EMAIL, null, null, null, null, null,
                                List.of("zhang@example.com"), null)));
        assertEquals("https://hook.example.com/x?***",
                NotificationRecordRules.receiverSummary(
                        msg(Channel.WEBHOOK, null, null, null, "https://hook.example.com/x?token=t1",
                                null, null, null)));
        assertEquals("广播",
                NotificationRecordRules.receiverSummary(msg(Channel.WEBSOCKET, null, null, null,
                        null, null, null, null)));
        assertEquals("用户:1,2",
                NotificationRecordRules.receiverSummary(
                        msg(Channel.WEBSOCKET, List.of(1L, 2L), null, null, null, null, null, null)));
    }

    @Test
    @DisplayName("receiverSummary：IN_APP 按 用户/角色/机构 维度拼接，全空为未知")
    void receiverSummary_inApp() {
        assertEquals("用户:1,2；角色:3；机构:4,5",
                NotificationRecordRules.receiverSummary(
                        msg(Channel.IN_APP, List.of(1L, 2L), List.of(3L), List.of(4L, 5L),
                                null, null, null, null)));
        assertEquals("未知",
                NotificationRecordRules.receiverSummary(
                        msg(Channel.IN_APP, null, null, null, null, null, null, null)));
        // 空列表维度不出现在摘要中
        assertEquals("角色:3",
                NotificationRecordRules.receiverSummary(
                        msg(Channel.IN_APP, List.of(), List.of(3L), null, null, null, null, null)));
    }

    @Test
    @DisplayName("receiverCount：按渠道取权威口径（WEBHOOK 记 1，广播记 0）")
    void receiverCount_channels() {
        assertEquals(2, NotificationRecordRules.receiverCount(
                msg(Channel.SMS, null, null, null, null, null, null,
                        List.of("13800008000", "13900009000"))));
        assertEquals(1, NotificationRecordRules.receiverCount(
                msg(Channel.EMAIL, null, null, null, null, null,
                        List.of("a@x.com"), null)));
        assertEquals(1, NotificationRecordRules.receiverCount(
                msg(Channel.WEBHOOK, null, null, null, "https://x.com/y", null, null, null)));
        assertEquals(0, NotificationRecordRules.receiverCount(
                msg(Channel.WEBSOCKET, null, null, null, null, null, null, null)));
        assertEquals(3, NotificationRecordRules.receiverCount(
                msg(Channel.WEBSOCKET, List.of(1L, 2L, 3L), null, null, null, null, null, null)));
        assertEquals(0, NotificationRecordRules.receiverCount(
                msg(Channel.IN_APP, List.of(), List.of(), List.of(), null, null, null, null)));
    }

    @Test
    @DisplayName("receiverSummary/Count：null 消息与 null 渠道防御")
    void nullSafety() {
        assertEquals("", NotificationRecordRules.receiverSummary(null));
        assertEquals(0, NotificationRecordRules.receiverCount(null));
        NotificationMessage noChannel = msg(null, List.of(1L), null, null, null, null, null, null);
        assertEquals("", NotificationRecordRules.receiverSummary(noChannel));
        assertEquals(0, NotificationRecordRules.receiverCount(noChannel));
    }

    @Test
    @DisplayName("truncate：null 原样返回，超长截取不加省略号")
    void truncate() {
        assertNull(NotificationRecordRules.truncate(null, 10));
        assertEquals("", NotificationRecordRules.truncate("abc", 0));
        assertEquals("abc", NotificationRecordRules.truncate("abc", 10));
        assertEquals("ab", NotificationRecordRules.truncate("abc", 2));
    }
}
