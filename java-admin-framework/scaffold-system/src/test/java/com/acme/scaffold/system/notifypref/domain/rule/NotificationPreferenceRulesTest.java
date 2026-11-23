package com.acme.scaffold.system.notifypref.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.notification.Channel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 通知偏好纯函数规则单测（E6）：归一、总开关、生效判定、接收者/地址过滤、16 格矩阵。 */
class NotificationPreferenceRulesTest {

    @Test
    void shouldNormalizeChannelsAndMsgTypes() {
        assertEquals(4, NotificationPreferenceRules.SUPPORTED_CHANNELS.size());
        assertFalse(NotificationPreferenceRules.supportsPreference(null));
        assertTrue(NotificationPreferenceRules.supportsPreference(Channel.IN_APP));
        assertFalse(NotificationPreferenceRules.supportsPreference(Channel.WEBHOOK));

        assertEquals("EMAIL", NotificationPreferenceRules.normalizeChannel(" email "));
        assertNull(NotificationPreferenceRules.normalizeChannel("webhook"));
        assertNull(NotificationPreferenceRules.normalizeChannel(null));

        assertEquals("NOTICE", NotificationPreferenceRules.normalizeMsgType(null));
        assertEquals("ALERT", NotificationPreferenceRules.normalizeMsgType(" alert "));
        assertNull(NotificationPreferenceRules.normalizeMsgType("unknown"));
        assertEquals("NOTICE", NotificationPreferenceRules.filterKey(null));
        assertEquals("CUSTOM", NotificationPreferenceRules.filterKey(" custom "));
    }

    @Test
    void shouldMapChannelMasterColumnsAndDefaults() {
        assertEquals("notify_site_message", NotificationPreferenceRules.channelColumn(Channel.IN_APP));
        assertEquals("notify_email", NotificationPreferenceRules.channelColumn(Channel.EMAIL));
        assertEquals("notify_mobile", NotificationPreferenceRules.channelColumn(Channel.SMS));
        assertNull(NotificationPreferenceRules.channelColumn(Channel.WEBSOCKET));
        assertNull(NotificationPreferenceRules.channelColumn(null));

        assertFalse(NotificationPreferenceRules.channelDefault(Channel.SMS));
        assertTrue(NotificationPreferenceRules.channelDefault(Channel.EMAIL));
        assertTrue(NotificationPreferenceRules.channelSwitch(null, Channel.EMAIL));
        assertFalse(NotificationPreferenceRules.channelSwitch(null, Channel.SMS));
        assertFalse(NotificationPreferenceRules.channelSwitch(0, Channel.EMAIL));
        assertTrue(NotificationPreferenceRules.channelSwitch(1, Channel.SMS));
    }

    @Test
    void shouldTwoLevelEnablement() {
        Map<Long, Boolean> master = Map.of(1L, true, 2L, false);
        Map<Long, Boolean> rows = Map.of(1L, false, 3L, true);
        assertFalse(NotificationPreferenceRules.isEnabled(Channel.EMAIL, 1L, rows, master)); // 行关闭
        assertFalse(NotificationPreferenceRules.isEnabled(Channel.EMAIL, 2L, rows, master)); // 总开关短路
        assertTrue(NotificationPreferenceRules.isEnabled(Channel.EMAIL, 3L, rows, master)); // 有行开启
        assertTrue(NotificationPreferenceRules.isEnabled(Channel.EMAIL, 4L, rows, master)); // 无行默认开
        assertTrue(NotificationPreferenceRules.isEnabled(Channel.EMAIL, 2L, rows, null)); // 总开关 map 为 null 时按列默认（EMAIL 开）
        assertFalse(NotificationPreferenceRules.isEnabled(Channel.SMS, 4L, rows, null)); // SMS 默认关
        assertFalse(NotificationPreferenceRules.isEnabled(Channel.EMAIL, null, rows, master));
    }

    @Test
    void shouldFilterReceiversPreservingOrder() {
        List<Long> result = NotificationPreferenceRules.filterReceivers(Channel.EMAIL,
                java.util.Arrays.asList(1L, 2L, 3L, null),
                Map.of(1L, false), Map.of(2L, false));
        assertEquals(List.of(3L), result);
        assertTrue(NotificationPreferenceRules.filterReceivers(Channel.EMAIL, null, null, null).isEmpty());
    }

    @Test
    void shouldFilterAddressesWithCaseInsensitiveKeys() {
        List<String> result = NotificationPreferenceRules.filterAddresses(Channel.EMAIL,
                java.util.Arrays.asList(" A@X.COM ", "b@x.com", "c@x.com", null, "  "),
                Map.of("a@x.com", 1L, "b@x.com", 2L),
                Map.of(1L, false), Map.of(2L, false), true);
        // a 退订、b 总关、c 无匹配放行、null/空白保留
        assertEquals(3, result.size());
        assertTrue(result.contains("c@x.com"));
        assertTrue(result.contains(null));
        assertTrue(result.contains("  "));
    }

    @Test
    void shouldBuildMatrixWithSourceAndMasterShortCircuit() {
        var matrix = NotificationPreferenceRules.buildMatrix(
                Map.of(Channel.EMAIL, false),
                Map.of(Channel.IN_APP, Map.of("NOTICE", false)));
        assertEquals(4, matrix.channels().size());
        var email = matrix.channels().stream().filter(c -> c.channel().equals("EMAIL")).findFirst().orElseThrow();
        assertFalse(email.channelEnabled());
        assertEquals(4, email.cells().size());
        assertTrue(email.cells().stream().allMatch(c -> !c.enabled()));
        assertTrue(email.cells().stream().allMatch(c -> c.source().equals(NotificationPreferenceRules.SOURCE_CHANNEL)));

        var inApp = matrix.channels().stream().filter(c -> c.channel().equals("IN_APP")).findFirst().orElseThrow();
        assertEquals(NotificationPreferenceRules.SOURCE_PREFERENCE, inApp.cells().get(0).source());
        assertFalse(inApp.cells().get(0).enabled());
        assertEquals(NotificationPreferenceRules.SOURCE_DEFAULT, inApp.cells().get(1).source());
        assertTrue(inApp.cells().get(1).enabled());
    }
}
