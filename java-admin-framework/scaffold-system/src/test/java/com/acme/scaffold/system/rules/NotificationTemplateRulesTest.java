package com.acme.scaffold.system.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NotificationTemplateRules} 纯函数规则测试：无 Spring 上下文，离线可跑。
 * 覆盖渠道归一、变量提取与去重、渲染（含未知变量保形与特殊字符安全）、模板结构校验。
 */
class NotificationTemplateRulesTest {

    @Test
    @DisplayName("normalizeChannel：大小写不敏感匹配，非法返回 null")
    void normalizeChannel_matchesCaseInsensitively() {
        assertEquals("EMAIL", NotificationTemplateRules.normalizeChannel("email"));
        assertEquals("IN_APP", NotificationTemplateRules.normalizeChannel("IN_APP"));
        assertNull(NotificationTemplateRules.normalizeChannel("WECHAT"));
        assertNull(NotificationTemplateRules.normalizeChannel(""));
        assertNull(NotificationTemplateRules.normalizeChannel(null));
        assertTrue(NotificationTemplateRules.isValidChannel("SMS"));
        assertFalse(NotificationTemplateRules.isValidChannel("foo"));
    }

    @Test
    @DisplayName("extractVariables：提取 ${var}，保序去重，容忍变量名前后空白")
    void extractVariables_ordersAndDedups() {
        assertEquals(List.of(), NotificationTemplateRules.extractVariables(null));
        assertEquals(List.of(), NotificationTemplateRules.extractVariables(""));
        assertEquals(List.of("orderNo", "amount"),
                NotificationTemplateRules.extractVariables("单号${orderNo}，金额${amount}，再次${orderNo}"));
        assertEquals(List.of("name"),
                NotificationTemplateRules.extractVariables("hi ${ name } there"));
        assertEquals(0, NotificationTemplateRules.countVariables("无变量文本"));
    }

    @Test
    @DisplayName("render：替换已知变量，未知保持原样，特殊字符安全")
    void render_substitutesKnownLeavesUnknownAndHandlesSpecials() {
        String tpl = "单号${orderNo}，金额${amount}";
        assertEquals("单号NO1，金额$100",
                NotificationTemplateRules.render(tpl, Map.of("orderNo", "NO1", "amount", "$100")));

        // 未知变量保持 ${name} 原样（预览安全）
        assertEquals("hi ${missing}", NotificationTemplateRules.render("hi ${missing}", Map.of()));
        // 替换值中的 $ 与反斜杠按字面量处理，避免正则注入
        assertEquals("val a$b\\c", NotificationTemplateRules.render("val ${x}", Map.of("x", "a$b\\c")));
        // 空内容返回空串
        assertEquals("", NotificationTemplateRules.render(null, Map.of("x", "1")));
    }

    @Test
    @DisplayName("validate：结构校验，合法返回空，非法按项报错")
    void validate_reportsStructuralErrors() {
        assertTrue(NotificationTemplateRules.validate("ORDER_SHIPPED", "发货通知", "EMAIL",
                "单号${orderNo}").isEmpty());

        assertFalse(NotificationTemplateRules.validate("order", "x", "EMAIL", "c").isEmpty());   // 编码小写
        assertFalse(NotificationTemplateRules.validate("ORDER_SHIPPED", "", "EMAIL", "c").isEmpty()); // 名称为空
        assertFalse(NotificationTemplateRules.validate("ORDER_SHIPPED", "x", "WECHAT", "c").isEmpty()); // 渠道非法
        assertFalse(NotificationTemplateRules.validate("ORDER_SHIPPED", "x", "EMAIL", "").isEmpty());  // 内容空
    }
}
