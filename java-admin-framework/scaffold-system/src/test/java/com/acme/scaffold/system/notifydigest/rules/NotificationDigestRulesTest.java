package com.acme.scaffold.system.notifydigest.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NotificationDigestRules} 离线单测：配置匹配、消息类型归一、单行摘要截断、
 * 条目保留（最近 N 条）、摘要正文渲染（带全量计数后缀）。
 *
 * <p>全部为纯函数断言，不依赖 Spring 上下文与数据库。</p>
 */
class NotificationDigestRulesTest {

    // ------------------------------------------------------------------
    // 配置匹配
    // ------------------------------------------------------------------

    @Test
    @DisplayName("matchesConfigured：白名单大小写不敏感，空白回退缺省类型")
    void matchesConfigured() {
        assertTrue(NotificationDigestRules.matchesConfigured("NOTICE",
                List.of("NOTICE", "ALERT")));
        assertTrue(NotificationDigestRules.matchesConfigured("notice",
                List.of("NOTICE")));
        assertTrue(NotificationDigestRules.matchesConfigured("  Notice ",
                List.of("notice")));
        // 消息类型空白 → 归一为 NOTICE，命中含 NOTICE 的配置
        assertTrue(NotificationDigestRules.matchesConfigured("   ",
                List.of("NOTICE")));
        // 配置空白 → 归一为 NOTICE，空白元素被忽略
        assertTrue(NotificationDigestRules.matchesConfigured("NOTICE",
                List.of("", "NOTICE")));
    }

    @Test
    @DisplayName("matchesConfigured：未命中/空配置/空类型返回 false")
    void matchesConfigured_negative() {
        assertFalse(NotificationDigestRules.matchesConfigured("ALERT",
                List.of("NOTICE")));
        assertFalse(NotificationDigestRules.matchesConfigured("NOTICE",
                List.of("ALERT", "SYSTEM")));
        assertFalse(NotificationDigestRules.matchesConfigured("NOTICE",
                List.of()));
        assertFalse(NotificationDigestRules.matchesConfigured("NOTICE", null));
        // 空配置 + 空白类型：配置空直接短路 false（不回退缺省）
        assertFalse(NotificationDigestRules.matchesConfigured("   ",
                List.of()));
    }

    @Test
    @DisplayName("normalizeMsgType：trim+大写，空白回退缺省 NOTICE")
    void normalizeMsgType() {
        assertEquals("NOTICE", NotificationDigestRules.normalizeMsgType("notice"));
        assertEquals("ALERT", NotificationDigestRules.normalizeMsgType("  Alert "));
        assertEquals("NOTICE", NotificationDigestRules.normalizeMsgType(null));
        assertEquals("NOTICE", NotificationDigestRules.normalizeMsgType("   "));
    }

    // ------------------------------------------------------------------
    // 单行摘要
    // ------------------------------------------------------------------

    @Test
    @DisplayName("summarizeItem：标题优先正文回退，trim+空白折叠，超长截断至 80")
    void summarizeItem() {
        assertEquals("标题", NotificationDigestRules.summarizeItem("标题", "正文"));
        assertEquals("正文", NotificationDigestRules.summarizeItem("   ", "  正文  "));
        assertEquals("", NotificationDigestRules.summarizeItem("   ", "  "));
        assertEquals("", NotificationDigestRules.summarizeItem(null, null));
        // 控制符折叠为单空格
        assertEquals("a b c", NotificationDigestRules.summarizeItem("a\r\nb\t c", null));
        // 80 字符截断（不追加省略号）
        String longText = "x".repeat(200);
        String out = NotificationDigestRules.summarizeItem(longText, null);
        assertEquals(NotificationDigestRules.MAX_ITEM_LEN, out.length());
        assertEquals("x".repeat(NotificationDigestRules.MAX_ITEM_LEN), out);
    }

    // ------------------------------------------------------------------
    // 条目保留
    // ------------------------------------------------------------------

    @Test
    @DisplayName("appendItem：追加并保留最近 maxItems 条，入参不被改写")
    void appendItem() {
        List<String> base = new java.util.ArrayList<>(List.of("a", "b"));
        List<String> out = NotificationDigestRules.appendItem(base, "c", 2);
        assertEquals(List.of("b", "c"), out);
        // 入参不被改动
        assertEquals(List.of("a", "b"), base);
        // 未超上限保留全部
        assertEquals(List.of("a", "b", "c"),
                NotificationDigestRules.appendItem(List.of("a", "b"), "c", 5));
        // maxItems<=0 至少保留最新一条
        assertEquals(List.of("c"),
                NotificationDigestRules.appendItem(List.of("a", "b"), "c", 0));
        // null 入参安全
        assertEquals(List.of("c"),
                NotificationDigestRules.appendItem(null, "c", 3));
    }

    // ------------------------------------------------------------------
    // 摘要正文渲染
    // ------------------------------------------------------------------

    @Test
    @DisplayName("renderItems：编号列表，全量超出保留数时追加『共N条』后缀")
    void renderItems() {
        assertEquals("", NotificationDigestRules.renderItems(List.of(), 0));
        assertEquals("", NotificationDigestRules.renderItems(null, 0));
        assertEquals("1. a\n2. b",
                NotificationDigestRules.renderItems(List.of("a", "b"), 2));
        // itemCount 未超出保留数：省略后缀
        assertEquals("1. a\n2. b",
                NotificationDigestRules.renderItems(List.of("a", "b"), 2));
        // itemCount 超出保留数：追加共N条
        assertEquals("1. a\n2. b\n…共5条",
                NotificationDigestRules.renderItems(List.of("a", "b"), 5));
    }
}
