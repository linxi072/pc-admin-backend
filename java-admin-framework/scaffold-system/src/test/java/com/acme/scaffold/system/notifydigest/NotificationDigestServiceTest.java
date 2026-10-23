package com.acme.scaffold.system.notifydigest;

import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.system.notification.config.NotificationDigestProperties;
import com.acme.scaffold.system.notifypref.NotificationPreferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link NotificationDigestService} 单元测试：覆盖纯预检 {@code shouldDigest} 各分支
 * （开关关闭 / 消息 null / 类型未命中白名单 / WEBHOOK 不聚合 / IN_APP 广播无 receiverIds /
 * IN_APP 有 receiverIds / EMAIL 无地址 / EMAIL/SMS 有地址）与入口 {@code enqueue} 短路
 * （不应聚合时直接返回 {@code NOT_DIGESTED}、绝不触库、不查外部服务——保证挂点
 * 在直通路径上零 DB 开销）。
 *
 * <p>DB 写入路径（查现存→更新或插入、findDue、buildDigestMessage 现查用户地址、
 * delete）按项目约定由真实 MySQL 集成测试覆盖——mock jOOQ 链式 API 既脆弱又偏离
 * 真实实现；故此处仅固化「是否聚合」的决策口径与零副作用不变量，
 * 与 M13/M14 同范式（不 mock jOOQ）。</p>
 */
class NotificationDigestServiceTest {

    private DSLContext dsl;
    private NotificationDigestProperties properties;
    private NotificationPreferenceService preferenceService;
    private ObjectMapper objectMapper;
    private NotificationDigestService service;

    @BeforeEach
    void setUp() {
        dsl = mock(DSLContext.class);
        properties = mock(NotificationDigestProperties.class);
        preferenceService = mock(NotificationPreferenceService.class);
        objectMapper = mock(ObjectMapper.class);
        service = new NotificationDigestService(dsl, properties, preferenceService, objectMapper);
    }

    // ------------------------------------------------------------------
    // shouldDigest 纯预检分支
    // ------------------------------------------------------------------

    @Test
    @DisplayName("shouldDigest：开关关闭 → false（不触库、不查外部服务）")
    void shouldDigest_disabledReturnsFalse() {
        when(properties.isEnabled()).thenReturn(false);
        NotificationMessage msg = new NotificationMessage(Channel.IN_APP, "t", "c", "NOTICE",
                List.of(1L), null, null, null, null, null, null);

        assertFalse(service.shouldDigest(msg));
        verifyNoInteractions(dsl, preferenceService, objectMapper);
    }

    @Test
    @DisplayName("shouldDigest：消息为 null → false")
    void shouldDigest_nullReturnsFalse() {
        when(properties.isEnabled()).thenReturn(true);

        assertFalse(service.shouldDigest(null));
        verifyNoInteractions(dsl, preferenceService, objectMapper);
    }

    @Test
    @DisplayName("shouldDigest：消息类型未命中白名单 → false")
    void shouldDigest_typeNotConfiguredReturnsFalse() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getMsgTypes()).thenReturn(List.of("NOTICE"));
        NotificationMessage msg = new NotificationMessage(Channel.IN_APP, "t", "c", "ALERT",
                List.of(1L), null, null, null, null, null, null);

        assertFalse(service.shouldDigest(msg));
        verifyNoInteractions(dsl, preferenceService, objectMapper);
    }

    @Test
    @DisplayName("shouldDigest：WEBHOOK 外部地址不聚合 → false")
    void shouldDigest_webhookNotDigestible() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getMsgTypes()).thenReturn(List.of("NOTICE"));
        NotificationMessage msg = new NotificationMessage(Channel.WEBHOOK, "t", "c", "NOTICE",
                null, null, null, "https://hook.example.com/x", null, null, null);

        assertFalse(service.shouldDigest(msg));
        verifyNoInteractions(dsl, preferenceService, objectMapper);
    }

    @Test
    @DisplayName("shouldDigest：IN_APP 广播（无 receiverIds）无个人投递箱 → false")
    void shouldDigest_inAppBroadcastNoReceiverIds() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getMsgTypes()).thenReturn(List.of("NOTICE"));
        NotificationMessage msg = new NotificationMessage(Channel.IN_APP, "t", "c", "NOTICE",
                null, null, null, null, null, null, null);

        assertFalse(service.shouldDigest(msg));
        verifyNoInteractions(dsl, preferenceService, objectMapper);
    }

    @Test
    @DisplayName("shouldDigest：IN_APP 有 receiverIds → true（不调外部服务）")
    void shouldDigest_inAppWithReceiverIds() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getMsgTypes()).thenReturn(List.of("NOTICE"));
        NotificationMessage msg = new NotificationMessage(Channel.IN_APP, "t", "c", "NOTICE",
                List.of(1L, 2L), null, null, null, null, null, null);

        assertTrue(service.shouldDigest(msg));
        verifyNoInteractions(dsl, preferenceService, objectMapper);
    }

    @Test
    @DisplayName("shouldDigest：EMAIL 无 emails 地址 → false")
    void shouldDigest_emailNoEmails() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getMsgTypes()).thenReturn(List.of("NOTICE"));
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "t", "c", "NOTICE",
                null, null, null, null, null, null, null);

        assertFalse(service.shouldDigest(msg));
        verifyNoInteractions(dsl, preferenceService, objectMapper);
    }

    @Test
    @DisplayName("shouldDigest：EMAIL 有 emails → true（预检不调 fetchAddressUserIds）")
    void shouldDigest_emailWithEmails() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getMsgTypes()).thenReturn(List.of("NOTICE"));
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "t", "c", "NOTICE",
                null, null, null, null, null, List.of("a@x.com"), null);

        assertTrue(service.shouldDigest(msg));
        // 预检仅校验目标形态，地址→用户反查由 enqueue 承担；此处不得触外部服务
        verifyNoInteractions(dsl, preferenceService, objectMapper);
    }

    @Test
    @DisplayName("shouldDigest：SMS 有 mobiles → true")
    void shouldDigest_smsWithMobiles() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getMsgTypes()).thenReturn(List.of("NOTICE"));
        NotificationMessage msg = new NotificationMessage(Channel.SMS, "t", "c", "NOTICE",
                null, null, null, null, null, null, List.of("13800000000"));

        assertTrue(service.shouldDigest(msg));
        verifyNoInteractions(dsl, preferenceService, objectMapper);
    }

    // ------------------------------------------------------------------
    // enqueue 短路（零 DB 开销）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("enqueue：不应聚合时返回 NOT_DIGESTED 单例，绝不触库/查外部服务")
    void enqueue_shortCircuitsWithoutDbWhenNotDigestible() {
        when(properties.isEnabled()).thenReturn(false);
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "t", "c", "NOTICE",
                null, null, null, null, null, List.of("a@x.com"), null);

        NotificationDigestService.EnqueueResult result = service.enqueue(msg);

        assertSame(NotificationDigestService.NOT_DIGESTED, result);
        assertFalse(result.digested());
        verifyNoInteractions(dsl, preferenceService, objectMapper);
    }

    @Test
    @DisplayName("enqueue：定向目标为空（IN_APP 广播）→ 直通，无 DB 副作用")
    void enqueue_shortCircuitsOnEmptyTargets() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getMsgTypes()).thenReturn(List.of("NOTICE"));
        NotificationMessage msg = new NotificationMessage(Channel.IN_APP, "t", "c", "NOTICE",
                null, null, null, null, null, null, null);

        NotificationDigestService.EnqueueResult result = service.enqueue(msg);

        assertSame(NotificationDigestService.NOT_DIGESTED, result);
        verifyNoInteractions(dsl, preferenceService, objectMapper);
    }
}
