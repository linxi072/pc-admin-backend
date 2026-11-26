package com.acme.scaffold.common.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 通知渠道归一规则单测：大小写不敏感匹配与 JVM 默认 Locale 隔离。 */
class ChannelNormalizeRulesTest {

    private Locale originalLocale;

    @BeforeEach
    void rememberLocale() {
        originalLocale = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void shouldNormalizeEveryChannelCaseInsensitively() {
        for (Channel channel : Channel.values()) {
            assertEquals(channel.name(), ChannelNormalizeRules.normalizeChannel(
                    channel.name().toLowerCase(Locale.ROOT)));
        }
    }

    @Test
    void shouldNormalizeUnderTurkishDefaultLocale() {
        Locale.setDefault(new Locale("tr", "TR"));
        assertEquals("IN_APP", ChannelNormalizeRules.normalizeChannel("in_app"));
        assertEquals("DINGTALK", ChannelNormalizeRules.normalizeChannel("dingtalk"));
        assertEquals("EMAIL", ChannelNormalizeRules.normalizeChannel(" email "));
    }

    @Test
    void shouldReturnNullForBlankOrUnknownChannel() {
        assertNull(ChannelNormalizeRules.normalizeChannel(null));
        assertNull(ChannelNormalizeRules.normalizeChannel("  "));
        assertNull(ChannelNormalizeRules.normalizeChannel("in-app"));
    }
}
