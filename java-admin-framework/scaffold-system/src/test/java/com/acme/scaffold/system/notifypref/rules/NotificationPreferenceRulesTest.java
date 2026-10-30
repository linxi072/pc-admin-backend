package com.acme.scaffold.system.notifypref.rules;

import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.system.notifypref.rules.NotificationPreferenceRules.ChannelMatrix;
import com.acme.scaffold.system.notifypref.rules.NotificationPreferenceRules.NotificationPreferenceMatrix;
import com.acme.scaffold.system.notifypref.rules.NotificationPreferenceRules.PreferenceCell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NotificationPreferenceRules} 离线单测：渠道/类型归一、总开关映射、
 * 「细分优先、无行默认开」生效判定、接收者/地址分区与矩阵构建。
 *
 * <p>全部为纯函数断言，不依赖 Spring 上下文与数据库。两级生效模型
 * （渠道总开关 && 细分行，无行默认开）是 M13 的核心语义，为重点守护项。</p>
 */
class NotificationPreferenceRulesTest {

    // ------------------------------------------------------------------
    // 归一
    // ------------------------------------------------------------------

    @Test
    @DisplayName("normalizeChannel：白名单四渠道大小写不敏感，WEBHOOK/未知/空返回 null")
    void normalizeChannel() {
        assertEquals("IN_APP", NotificationPreferenceRules.normalizeChannel(" in_app "));
        assertEquals("EMAIL", NotificationPreferenceRules.normalizeChannel("Email"));
        assertEquals("SMS", NotificationPreferenceRules.normalizeChannel("sms"));
        assertEquals("WEBSOCKET", NotificationPreferenceRules.normalizeChannel("websocket"));
        // WEBHOOK 无个人偏好语义：拒绝建行
        assertNull(NotificationPreferenceRules.normalizeChannel("WEBHOOK"));
        assertNull(NotificationPreferenceRules.normalizeChannel("DINGTALK"));
        assertNull(NotificationPreferenceRules.normalizeChannel("  "));
        assertNull(NotificationPreferenceRules.normalizeChannel(null));
    }

    @Test
    @DisplayName("supportsPreference：四渠道 true，WEBHOOK/null false")
    void supportsPreference() {
        assertTrue(NotificationPreferenceRules.supportsPreference(Channel.IN_APP));
        assertTrue(NotificationPreferenceRules.supportsPreference(Channel.EMAIL));
        assertTrue(NotificationPreferenceRules.supportsPreference(Channel.SMS));
        assertTrue(NotificationPreferenceRules.supportsPreference(Channel.WEBSOCKET));
        assertFalse(NotificationPreferenceRules.supportsPreference(Channel.WEBHOOK));
        assertFalse(NotificationPreferenceRules.supportsPreference(null));
    }

    @Test
    @DisplayName("normalizeMsgType：空白归一 NOTICE，白名单内外分明")
    void normalizeMsgType() {
        assertEquals("NOTICE", NotificationPreferenceRules.normalizeMsgType(null));
        assertEquals("NOTICE", NotificationPreferenceRules.normalizeMsgType("   "));
        assertEquals("ALERT", NotificationPreferenceRules.normalizeMsgType(" alert "));
        assertEquals("SYSTEM", NotificationPreferenceRules.normalizeMsgType("system"));
        assertEquals("CC", NotificationPreferenceRules.normalizeMsgType("cc"));
        assertNull(NotificationPreferenceRules.normalizeMsgType("MARKETING"));
    }

    @Test
    @DisplayName("filterKey：白名单内归一，白名单外透传（无行匹配即默认开通，不误判退订）")
    void filterKey() {
        assertEquals("NOTICE", NotificationPreferenceRules.filterKey(null));
        assertEquals("NOTICE", NotificationPreferenceRules.filterKey(""));
        assertEquals("ALERT", NotificationPreferenceRules.filterKey("alert"));
        assertEquals("MARKETING", NotificationPreferenceRules.filterKey("Marketing"));
    }

    // ------------------------------------------------------------------
    // sys_user 渠道总开关映射
    // ------------------------------------------------------------------

    @Test
    @DisplayName("channelColumn：三渠道映射列名，WEBSOCKET/WEBHOOK 无列")
    void channelColumn() {
        assertEquals("notify_site_message",
                NotificationPreferenceRules.channelColumn(Channel.IN_APP));
        assertEquals("notify_email", NotificationPreferenceRules.channelColumn(Channel.EMAIL));
        assertEquals("notify_mobile", NotificationPreferenceRules.channelColumn(Channel.SMS));
        assertNull(NotificationPreferenceRules.channelColumn(Channel.WEBSOCKET));
        assertNull(NotificationPreferenceRules.channelColumn(Channel.WEBHOOK));
        assertNull(NotificationPreferenceRules.channelColumn(null));
    }

    @Test
    @DisplayName("channelDefault：IN_APP/EMAIL 默认开，SMS 默认关（选择性订阅），WEBSOCKET 恒开")
    void channelDefault() {
        assertTrue(NotificationPreferenceRules.channelDefault(Channel.IN_APP));
        assertTrue(NotificationPreferenceRules.channelDefault(Channel.EMAIL));
        assertFalse(NotificationPreferenceRules.channelDefault(Channel.SMS));
        assertTrue(NotificationPreferenceRules.channelDefault(Channel.WEBSOCKET));
    }

    @Test
    @DisplayName("channelSwitch：0/1 直读，null 按列默认值兜底")
    void channelSwitch() {
        assertTrue(NotificationPreferenceRules.channelSwitch(1, Channel.IN_APP));
        assertFalse(NotificationPreferenceRules.channelSwitch(0, Channel.IN_APP));
        // null 兜底：IN_APP 默认开、SMS 默认关
        assertTrue(NotificationPreferenceRules.channelSwitch(null, Channel.IN_APP));
        assertFalse(NotificationPreferenceRules.channelSwitch(null, Channel.SMS));
    }

    // ------------------------------------------------------------------
    // 生效判定与分区
    // ------------------------------------------------------------------

    @Test
    @DisplayName("isEnabled：总开关短路一切；有行按行值；无行默认开")
    void isEnabled_twoLevelModel() {
        Map<Long, Boolean> rows = new LinkedHashMap<>();
        rows.put(1L, false);   // 用户 1 显式退订
        rows.put(2L, true);    // 用户 2 显式订阅
        Map<Long, Boolean> master = new LinkedHashMap<>();
        master.put(1L, true);
        master.put(2L, true);
        master.put(3L, true);  // 无行 → 默认开
        master.put(4L, false); // 总开关关 → 短路，行值无效

        assertFalse(NotificationPreferenceRules.isEnabled(Channel.IN_APP, 1L, rows, master));
        assertTrue(NotificationPreferenceRules.isEnabled(Channel.IN_APP, 2L, rows, master));
        assertTrue(NotificationPreferenceRules.isEnabled(Channel.IN_APP, 3L, rows, master));
        assertFalse(NotificationPreferenceRules.isEnabled(Channel.IN_APP, 4L, rows, master));
        assertFalse(NotificationPreferenceRules.isEnabled(Channel.IN_APP, null, rows, master));
    }

    @Test
    @DisplayName("isEnabled：用户缺失时总开关按列默认值兜底（SMS 默认关）")
    void isEnabled_missingUserFallsBackToColumnDefault() {
        // 用户 5 既无行也不在 master map：IN_APP 兜底为开，SMS 兜底为关
        assertTrue(NotificationPreferenceRules.isEnabled(Channel.IN_APP, 5L, Map.of(), Map.of()));
        assertFalse(NotificationPreferenceRules.isEnabled(Channel.SMS, 5L, Map.of(), Map.of()));
    }

    @Test
    @DisplayName("filterReceivers：保序过滤、null 剔除、不去重")
    void filterReceivers() {
        Map<Long, Boolean> rows = Map.of(1L, false, 3L, true);
        Map<Long, Boolean> master = Map.of(1L, true, 2L, true, 3L, true, 4L, false);

        // Arrays.asList 允许 null 元素（List.of 不允许），用于验证 null 接收者被剔除
        List<Long> kept = NotificationPreferenceRules.filterReceivers(Channel.IN_APP,
                java.util.Arrays.asList(1L, 2L, 3L, null, 4L, 2L), rows, master);
        // 1 退订剔除；2 无行默认开；3 有行开；null 剔除；4 总开关关剔除；2 重复保留
        assertEquals(List.of(2L, 3L, 2L), kept);
    }

    @Test
    @DisplayName("filterAddresses：外部地址默认放行，退订用户地址剔除，空白保留")
    void filterAddresses() {
        Map<String, Long> addressUserIds = new LinkedHashMap<>();
        addressUserIds.put("opted.out@x.com", 1L);
        addressUserIds.put("kept@x.com", 2L);
        Map<Long, Boolean> rows = Map.of(1L, false, 2L, true);
        Map<Long, Boolean> master = Map.of(1L, true, 2L, true);

        List<String> kept = NotificationPreferenceRules.filterAddresses(Channel.EMAIL,
                List.of("OPTED.OUT@X.com", "kept@x.com", "outsider@y.com", "  "),
                addressUserIds, rows, master, true);
        // email 键大小写不敏感：1 退订剔除；2 保留；外部地址无匹配放行；空白原样保留
        assertEquals(List.of("kept@x.com", "outsider@y.com", "  "), kept);
    }

    @Test
    @DisplayName("filterAddresses：mobile 键大小写敏感（trim 归一）")
    void filterAddresses_mobile() {
        Map<String, Long> addressUserIds = Map.of("13800008000", 1L);
        Map<Long, Boolean> rows = Map.of(1L, false);
        Map<Long, Boolean> master = Map.of(1L, true);

        List<String> kept = NotificationPreferenceRules.filterAddresses(Channel.SMS,
                List.of(" 13800008000 ", "13800008001"), addressUserIds, rows, master, false);
        // 1 退订剔除；未注册号码无匹配放行
        assertEquals(List.of("13800008001"), kept);
    }

    // ------------------------------------------------------------------
    // 矩阵构建
    // ------------------------------------------------------------------

    @Test
    @DisplayName("buildMatrix：16 格、三来源、总开关整行短路")
    void buildMatrix() {
        Map<Channel, Boolean> master = new LinkedHashMap<>();
        master.put(Channel.IN_APP, true);
        master.put(Channel.EMAIL, false);   // 总开关关：整行 CHANNEL 来源
        master.put(Channel.SMS, true);
        // WEBSOCKET 不放入：按列默认值（恒开）兜底

        Map<Channel, Map<String, Boolean>> rows = new LinkedHashMap<>();
        rows.put(Channel.IN_APP, Map.of("NOTICE", false));       // 显式退订一格
        rows.put(Channel.SMS, Map.of("ALERT", true, "CC", false));

        NotificationPreferenceMatrix matrix = NotificationPreferenceRules.buildMatrix(master, rows);

        assertEquals(4, matrix.channels().size());
        assertEquals(List.of("IN_APP", "EMAIL", "SMS", "WEBSOCKET"),
                matrix.channels().stream().map(ChannelMatrix::channel).toList());

        ChannelMatrix inApp = matrix.channels().get(0);
        assertTrue(inApp.channelEnabled());
        assertEquals(4, inApp.cells().size());
        assertEquals(List.of("NOTICE", "ALERT", "SYSTEM", "CC"),
                inApp.cells().stream().map(PreferenceCell::msgType).toList());
        PreferenceCell notice = inApp.cells().get(0);
        assertFalse(notice.enabled());
        assertEquals(NotificationPreferenceRules.SOURCE_PREFERENCE, notice.source());
        // 同渠道其余三格无行默认开
        assertEquals(NotificationPreferenceRules.SOURCE_DEFAULT, inApp.cells().get(1).source());
        assertTrue(inApp.cells().get(1).enabled());

        ChannelMatrix email = matrix.channels().get(1);
        assertFalse(email.channelEnabled());
        // 总开关关闭：整行强制关闭且来源为 CHANNEL（行值被短路）
        assertTrue(email.cells().stream().allMatch(c -> !c.enabled()
                && NotificationPreferenceRules.SOURCE_CHANNEL.equals(c.source())));

        ChannelMatrix sms = matrix.channels().get(2);
        assertTrue(sms.cells().get(1).enabled());   // ALERT 有行 true
        assertEquals(NotificationPreferenceRules.SOURCE_PREFERENCE, sms.cells().get(1).source());
        assertFalse(sms.cells().get(3).enabled());  // CC 有行 false
        assertEquals(NotificationPreferenceRules.SOURCE_PREFERENCE, sms.cells().get(3).source());
        assertTrue(sms.cells().get(0).enabled());   // NOTICE 无行默认开
        assertEquals(NotificationPreferenceRules.SOURCE_DEFAULT, sms.cells().get(0).source());

        // WEBSOCKET 无 master 入参：列默认恒开 + 无行默认开
        ChannelMatrix ws = matrix.channels().get(3);
        assertTrue(ws.channelEnabled());
        assertTrue(ws.cells().stream().allMatch(c -> c.enabled()
                && NotificationPreferenceRules.SOURCE_DEFAULT.equals(c.source())));
    }

    @Test
    @DisplayName("buildMatrix：null 入参防御（全部按默认值渲染）")
    void buildMatrix_nullSafety() {
        NotificationPreferenceMatrix matrix = NotificationPreferenceRules.buildMatrix(null, null);
        assertEquals(4, matrix.channels().size());
        // 全部无 master 无行：IN_APP/EMAIL/WEBSOCKET 默认开，SMS 默认关
        assertTrue(matrix.channels().get(0).channelEnabled());
        assertTrue(matrix.channels().get(1).channelEnabled());
        assertFalse(matrix.channels().get(2).channelEnabled());
        assertTrue(matrix.channels().get(3).channelEnabled());
    }
}
