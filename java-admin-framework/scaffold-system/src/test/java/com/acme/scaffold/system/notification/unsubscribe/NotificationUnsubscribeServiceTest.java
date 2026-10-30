package com.acme.scaffold.system.notification.unsubscribe;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.system.notification.unsubscribe.rules.UnsubscribeTokenRules;
import com.acme.scaffold.system.notification.unsubscribe.rules.UnsubscribeTokenRules.UnsubscribeTarget;
import com.acme.scaffold.system.notifypref.NotificationPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link NotificationUnsubscribeService} 测试：未激活短路、链接铸造与批量解析、
 * 退订执行（验签 + 写偏好 + 幂等）、无效令牌拒绝。
 *
 * <p>偏好服务以 Mockito 替身聚焦本服务编排逻辑；持久化语义由
 * {@code NotificationPreferenceServiceTest} 覆盖。
 */
@DisplayName("通知退订服务：链接铸造、批量解析与退订执行")
class NotificationUnsubscribeServiceTest {

    private static final String SECRET = "service-test-secret";
    private static final String BASE_URL = "https://admin.example.com/";
    private static final String TOKEN = UnsubscribeTokenRules.mint(
            SECRET, 42L, Channel.EMAIL, "NOTICE", Long.MAX_VALUE / 2);

    private final NotificationPreferenceService preferenceService =
            mock(NotificationPreferenceService.class);

    private NotificationUnsubscribeService service;
    private NotificationUnsubscribeProperties props;

    @BeforeEach
    void setUp() {
        props = new NotificationUnsubscribeProperties();
        props.setEnabled(true);
        props.setSecret(SECRET);
        props.setBaseUrl(BASE_URL);
        props.setTtlHours(72);
        service = new NotificationUnsubscribeService(props, preferenceService);
    }

    private NotificationUnsubscribeService inactiveService() {
        NotificationUnsubscribeProperties off = new NotificationUnsubscribeProperties();
        return new NotificationUnsubscribeService(off, preferenceService);
    }

    @Test
    @DisplayName("未激活：不铸链接、不解析、不反查用户")
    void inactive_shortCircuitsEverything() {
        NotificationUnsubscribeService off = inactiveService();

        assertFalse(off.isActive());
        assertNull(off.unsubscribeUrl(42L, Channel.EMAIL, "NOTICE"));
        assertTrue(off.resolveLinks(List.of("a@x.com"), true, Channel.EMAIL, "NOTICE")
                .isEmpty());
        verifyNoInteractions(preferenceService);
    }

    @Test
    @DisplayName("半配置（缺密钥或缺基址）同样视为未激活")
    void halfConfigured_isInactive() {
        props.setSecret(null);
        assertFalse(service.isActive());
        props.setSecret(SECRET);
        props.setBaseUrl("  ");
        assertFalse(service.isActive());
    }

    @Test
    @DisplayName("退订 URL：基址去尾斜杠 + 端点路径 + 可验签令牌")
    void unsubscribeUrl_buildsVerifiableLink() {
        String url = service.unsubscribeUrl(42L, Channel.EMAIL, "NOTICE");

        assertNotNull(url);
        assertTrue(url.startsWith("https://admin.example.com"
                + NotificationUnsubscribeService.UNSUBSCRIBE_PATH + "?token="));
        String token = url.substring(url.indexOf("token=") + 6);
        Optional<UnsubscribeTarget> parsed = UnsubscribeTokenRules.verify(
                SECRET, token, System.currentTimeMillis());
        assertTrue(parsed.isPresent());
        assertEquals(42L, parsed.get().userId());
        assertEquals(Channel.EMAIL, parsed.get().channel());
        assertEquals("NOTICE", parsed.get().msgType());
    }

    @Test
    @DisplayName("白名单外消息类型 / 不支持渠道 / null userId 不铸链接")
    void unsubscribeUrl_rejectsUnrepresentableTargets() {
        assertNull(service.unsubscribeUrl(42L, Channel.EMAIL, "UNKNOWN_TYPE"));
        assertNull(service.unsubscribeUrl(42L, Channel.WEBHOOK, "NOTICE"));
        assertNull(service.unsubscribeUrl(null, Channel.EMAIL, "NOTICE"));
    }

    @Test
    @DisplayName("空白消息类型按缺省 NOTICE 归一后铸链")
    void unsubscribeUrl_blankMsgTypeDefaultsToNotice() {
        String url = service.unsubscribeUrl(42L, Channel.EMAIL, "  ");
        assertNotNull(url);
        String token = url.substring(url.indexOf("token=") + 6);
        assertEquals("NOTICE", UnsubscribeTokenRules.verify(SECRET, token,
                System.currentTimeMillis()).get().msgType());
    }

    @Test
    @DisplayName("批量解析：地址反查用户后逐用户铸链，键为归一化地址")
    void resolveLinks_mintsPerResolvedUser() {
        Map<String, Long> addressUserIds = new LinkedHashMap<>();
        addressUserIds.put("a@x.com", 1L);
        addressUserIds.put("b@x.com", 2L);
        when(preferenceService.fetchAddressUserIds(any(), eq(true))).thenReturn(addressUserIds);

        Map<String, String> links = service.resolveLinks(
                List.of("a@x.com", "b@x.com"), true, Channel.EMAIL, "ALERT");

        assertEquals(2, links.size());
        for (Map.Entry<String, Long> e : addressUserIds.entrySet()) {
            String url = links.get(e.getKey());
            assertNotNull(url, "缺失去退订链接: " + e.getKey());
            String token = url.substring(url.indexOf("token=") + 6);
            UnsubscribeTarget t = UnsubscribeTokenRules.verify(
                    SECRET, token, System.currentTimeMillis()).get();
            assertEquals(e.getValue(), t.userId());
            assertEquals("ALERT", t.msgType());
        }
    }

    @Test
    @DisplayName("批量解析：无匹配用户 / 空地址列表不触达反查")
    void resolveLinks_noMatchOrEmpty() {
        when(preferenceService.fetchAddressUserIds(any(), eq(true))).thenReturn(Map.of());
        assertTrue(service.resolveLinks(List.of("external@x.com"), true,
                Channel.EMAIL, "NOTICE").isEmpty());
        assertTrue(service.resolveLinks(List.of(), true, Channel.EMAIL, "NOTICE").isEmpty());
        assertTrue(service.resolveLinks(null, true, Channel.EMAIL, "NOTICE").isEmpty());
    }

    @Test
    @DisplayName("退订执行：验签通过写偏好，返回变更结果")
    void unsubscribe_writesPreference() {
        when(preferenceService.unsubscribe(42L, Channel.EMAIL, "NOTICE")).thenReturn(true);

        NotificationUnsubscribeService.UnsubscribeOutcome outcome = service.unsubscribe(TOKEN);

        assertTrue(outcome.changed());
        assertEquals("EMAIL", outcome.channel());
        assertEquals("NOTICE", outcome.msgType());
        assertNotNull(outcome.message());
        verify(preferenceService).unsubscribe(42L, Channel.EMAIL, "NOTICE");
    }

    @Test
    @DisplayName("重复退订幂等：已是退订态时 changed=false 且不报错")
    void unsubscribe_idempotentWhenAlreadyOff() {
        when(preferenceService.unsubscribe(42L, Channel.EMAIL, "NOTICE")).thenReturn(false);

        NotificationUnsubscribeService.UnsubscribeOutcome outcome = service.unsubscribe(TOKEN);

        assertFalse(outcome.changed());
        verify(preferenceService).unsubscribe(42L, Channel.EMAIL, "NOTICE");
    }

    @Test
    @DisplayName("无效/篡改/过期令牌抛校验异常，不触达写路径")
    void unsubscribe_rejectsInvalidTokens() {
        String tampered = TOKEN.substring(0, TOKEN.length() - 3) + "AAA";
        String expired = UnsubscribeTokenRules.mint(SECRET, 42L, Channel.EMAIL, "NOTICE", 1000L);

        for (String bad : new String[] {null, "", "garbage", tampered, expired}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.unsubscribe(bad));
            assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }
        verify(preferenceService, never()).unsubscribe(anyLong(), any(Channel.class), any());
    }

    @Test
    @DisplayName("用另一密钥铸造的令牌被拒（密钥隔离）")
    void unsubscribe_rejectsForeignSecret() {
        String foreign = UnsubscribeTokenRules.mint("another-secret", 42L,
                Channel.EMAIL, "NOTICE", Long.MAX_VALUE / 2);
        assertThrows(BusinessException.class, () -> service.unsubscribe(foreign));
    }

    @Test
    @DisplayName("未激活服务执行退订：验签短路失败（安全失败）")
    void unsubscribe_whenInactive_alwaysFails() {
        NotificationUnsubscribeService off = inactiveService();
        assertThrows(BusinessException.class, () -> off.unsubscribe(TOKEN));
        verifyNoInteractions(preferenceService);
    }
}
