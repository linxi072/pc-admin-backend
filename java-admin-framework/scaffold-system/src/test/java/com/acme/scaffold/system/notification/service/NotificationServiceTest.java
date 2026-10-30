package com.acme.scaffold.system.notification.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.system.entity.SysNotificationTemplateDO;
import com.acme.scaffold.system.notification.config.NotificationFailoverProperties;
import com.acme.scaffold.system.notification.config.NotificationRetryProperties;
import com.acme.scaffold.system.notification.dto.SendWithTemplateNotificationRequest;
import com.acme.scaffold.system.notifydigest.NotificationDigestService;
import com.acme.scaffold.system.notifydigest.NotificationDigestService.EnqueueResult;
import com.acme.scaffold.system.notifydigest.domain.SysNotificationDigestDO;
import com.acme.scaffold.system.notifydigest.rules.NotificationDigestRules;
import com.acme.scaffold.system.notifylog.NotificationRecordRecorder;
import com.acme.scaffold.system.notifypref.NotificationPreferenceService;
import com.acme.scaffold.system.notifypref.rules.NotificationPreferenceRules;
import com.acme.scaffold.system.notifyquiet.NotificationQuietHoursService;
import com.acme.scaffold.system.notifyquiet.rules.NotificationQuietHoursRules;
import com.acme.scaffold.system.service.NotificationTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NotificationService} 单元测试：覆盖 M11 按模板发送的编排（取启用中模板 → 渲染 ${var}
 * → 按模板渠道走 {@code NotificationRouter} 路由到适配器）、M12 发送记录旁路
 * （dispatch 成功/失败均留痕、templateCode 透传）、M13 偏好过滤挂点
 * （过滤后消息才进适配器、全部退订记 SKIPPED 不调端口、偏好查询异常按 FAILED 处理）
 * 与 M14 免打扰挂点（时间维总闸先于偏好、全部抑制记 SKIPPED 且不再查偏好、
 * 免打扰查询异常按 FAILED 处理）、M16 有界重试与故障转移（瞬时故障按次数重试、
 * 业务拒绝不重试、耗尽即败、开启故障转移后切备适配器、关闭时不切）、
 * M17 聚合摘要挂点（挂起记 DIGEST_PENDING 返 0 不调端口、达阈值刷新摘要消息
 * 递归走完整 dispatch 且删行、摘要投递异常/无处可投仍删行不影响原消息结论）。
 * 模板服务、记录器、偏好/免打扰/聚合服务以 mock 替身，
 * 渠道适配器用捕获型 fake，全离线、不依赖数据库与 Spring 上下文。
 */
class NotificationServiceTest {

    private NotificationTemplateService templateService;
    private NotificationRecordRecorder recordRecorder;
    private NotificationPreferenceService preferenceService;
    private NotificationQuietHoursService quietHoursService;
    private NotificationRetryProperties retryProperties;
    private NotificationFailoverProperties failoverProperties;
    private NotificationDigestService digestService;
    private CapturingPort port;
    private NotificationService service;

    /** 捕获被投递消息的测试适配器（仅支持 EMAIL，支持失败注入与调用计数）。 */
    static final class CapturingPort implements NotificationPort {
        NotificationMessage last;
        int ret = 1;
        int calls;
        /** 每次调用都抛该异常（置空即恢复正常）。 */
        RuntimeException failWith;
        /** 前 N 次调用抛瞬时异常，之后成功（与 failWith 互斥，failWith 优先）。 */
        int failTimes;

        @Override
        public boolean supports(Channel channel) {
            return channel == Channel.EMAIL;
        }

        @Override
        public int dispatch(NotificationMessage message) {
            this.last = message;
            this.calls++;
            if (failWith != null) {
                throw failWith;
            }
            if (failTimes > 0) {
                failTimes--;
                throw new IllegalStateException("瞬时故障");
            }
            return ret;
        }
    }

    private static SysNotificationTemplateDO template(String title, String content, String channel, Integer enabled) {
        SysNotificationTemplateDO d = new SysNotificationTemplateDO();
        d.setId(1L);
        d.setTemplateCode("ORDER_SHIPPED");
        d.setTemplateName("发货通知");
        d.setChannel(channel);
        d.setTitle(title);
        d.setContent(content);
        d.setMsgType("NOTICE");
        d.setEnabled(enabled);
        return d;
    }

    /** 构造一行待刷新聚合摘要（EMAIL / NOTICE / 5 条）。 */
    private static SysNotificationDigestDO digestRow(Long id, Long userId) {
        SysNotificationDigestDO row = new SysNotificationDigestDO();
        row.setId(id);
        row.setUserId(userId);
        row.setChannel("EMAIL");
        row.setMsgType("NOTICE");
        row.setItemCount(5);
        row.setItemsJson("[\"订单已发货\",\"优惠券到账\"]");
        return row;
    }

    @BeforeEach
    void setUp() {
        templateService = mock(NotificationTemplateService.class);
        recordRecorder = mock(NotificationRecordRecorder.class);
        preferenceService = mock(NotificationPreferenceService.class);
        quietHoursService = mock(NotificationQuietHoursService.class);
        digestService = mock(NotificationDigestService.class);
        retryProperties = new NotificationRetryProperties();
        // 单测不等待真实退避：线性退避计算由 DeliveryRetryRulesTest 纯函数覆盖
        retryProperties.setBackoffMs(0);
        failoverProperties = new NotificationFailoverProperties();
        port = new CapturingPort();
        service = new NotificationService(List.of(port), templateService, recordRecorder,
                preferenceService, quietHoursService, retryProperties, failoverProperties,
                digestService);
        // 默认三道过滤/挂点均放行：原样返回入参消息（覆盖 M11/M12 各例的既有语义）
        when(quietHoursService.applyQuietHours(any(NotificationMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(preferenceService.applyPreferences(any(NotificationMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // 默认不聚合：直通投递（M17 各例按需覆写为 digested 结果）
        when(digestService.enqueue(any(NotificationMessage.class)))
                .thenReturn(NotificationDigestService.NOT_DIGESTED);
    }

    @Test
    @DisplayName("sendWithTemplate：渲染 ${var} 后按模板渠道/标题/正文投递")
    void sendWithTemplate_rendersAndDispatches() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenReturn(template("订单 ${orderNo} 已发货", "尊敬的会员，订单 ${orderNo} 已发货。", "EMAIL", 1));
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", Map.of("orderNo", "NO2026"),
                null, null, null, null, List.of("a@x.com"), null);

        int delivered = service.sendWithTemplate(req);

        assertEquals(1, delivered);
        assertEquals(Channel.EMAIL, port.last.channel());
        assertEquals("订单 NO2026 已发货", port.last.title());
        assertEquals("尊敬的会员，订单 NO2026 已发货。", port.last.content());
        assertEquals("NOTICE", port.last.msgType());
        assertEquals(List.of("a@x.com"), port.last.emails());
    }

    @Test
    @DisplayName("sendWithTemplate：未提供变量时 ${var} 保持原样（SMS 模板标题可为空）")
    void sendWithTemplate_missingVarsKeptAndNullTitle() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenReturn(template(null, "订单 ${orderNo} 已发货", "EMAIL", 1));
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", null, null, null, null, null, List.of("a@x.com"), null);

        service.sendWithTemplate(req);

        assertNull(port.last.title());
        assertEquals("订单 ${orderNo} 已发货", port.last.content());
    }

    @Test
    @DisplayName("sendWithTemplate：模板缺失/停用异常向上传播，不投递")
    void sendWithTemplate_propagatesTemplateError() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND, "通知模板不存在: ORDER_SHIPPED"));
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", Map.of(), null, null, null, null, List.of("a@x.com"), null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.sendWithTemplate(req));
        assertTrue(ex.getMessage().contains("模板不存在"));
    }

    @Test
    @DisplayName("sendWithTemplate：模板渠道无适配器时显式失败（不静默丢弃）")
    void sendWithTemplate_unsupportedChannelThrows() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenReturn(template("t", "c", "SMS", 1)); // 仅注册了 EMAIL 适配器
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", Map.of(), null, null, null, null, null, List.of("13800000000"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.sendWithTemplate(req));
        assertTrue(ex.getMessage().contains("不支持的通知渠道"));
    }

    // ------------------------------------------------------------------
    // M12：发送记录旁路
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dispatch：投递成功记录 SUCCESS + 实际投递数，原始直发 templateCode 为 null")
    void dispatch_successRecords() {
        port.ret = 2;

        int delivered = service.dispatch(new NotificationMessage(Channel.EMAIL, "标题", "正文"));

        assertEquals(2, delivered);
        verify(recordRecorder).record(any(NotificationMessage.class), isNull(), eq(2), eq(true), isNull());
    }

    @Test
    @DisplayName("dispatch：渠道无适配器时记录 FAILED + 异常消息并原样重抛")
    void dispatch_failureRecordsAndRethrows() {
        NotificationMessage msg = new NotificationMessage(Channel.SMS, null, "正文");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.dispatch(msg));

        assertTrue(ex.getMessage().contains("不支持的通知渠道"));
        verify(recordRecorder).record(eq(msg), isNull(), eq(0), eq(false),
                contains("不支持的通知渠道"));
    }

    @Test
    @DisplayName("sendWithTemplate：模板编码透传到发送记录（provenance 审计）")
    void sendWithTemplate_templateCodePropagatedToRecord() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenReturn(template("订单已发货", "尊敬的会员，订单已发货。", "EMAIL", 1));
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", Map.of(), null, null, null, null, List.of("a@x.com"), null);

        service.sendWithTemplate(req);

        verify(recordRecorder).record(any(NotificationMessage.class), eq("ORDER_SHIPPED"),
                eq(1), eq(true), isNull());
    }

    @Test
    @DisplayName("sendWithTemplate：投递失败同样记录 FAILED（含模板编码）并向上传播")
    void sendWithTemplate_failureRecordsWithTemplateCode() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenReturn(template("t", "c", "SMS", 1)); // 仅注册了 EMAIL 适配器
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", Map.of(), null, null, null, null, null, List.of("13800000000"));

        assertThrows(BusinessException.class, () -> service.sendWithTemplate(req));

        verify(recordRecorder).record(any(NotificationMessage.class), eq("ORDER_SHIPPED"),
                eq(0), eq(false), contains("不支持的通知渠道"));
    }

    // ------------------------------------------------------------------
    // M13：偏好过滤挂点
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dispatch：偏好过滤后的消息才进适配器，记录仍以原始消息为审计对象")
    void dispatch_preferenceFilteredMessageIsDispatched() {
        NotificationMessage original = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "NOTICE", null, null, null, null, null, List.of("a@x.com", "b@x.com"), null);
        NotificationMessage filtered = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "NOTICE", null, null, null, null, null, List.of("a@x.com"), null);
        when(preferenceService.applyPreferences(original)).thenReturn(filtered);

        int delivered = service.dispatch(original);

        assertEquals(1, delivered);
        assertSame(filtered, port.last);
        // 记录旁路以原始消息为对象：目标接收数反映发送意图，退订差异由状态码表达
        verify(recordRecorder).record(eq(original), isNull(), eq(1), eq(true), isNull());
    }

    @Test
    @DisplayName("dispatch：定向接收者全部退订 → 记 SKIPPED、不调适配器、静默返回 0")
    void dispatch_allUnsubscribedSkipsPortAndRecordsSkipped() {
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "ALERT", null, null, null, null, null, List.of("opted.out@x.com"), null);
        when(preferenceService.applyPreferences(msg)).thenReturn(null);

        int delivered = service.dispatch(msg);

        assertEquals(0, delivered);
        assertNull(port.last);
        // port 为手写捕获型 fake（非 Mockito mock），以「无消息进入适配器」断言替代 never() 验证
        verify(recordRecorder).skip(eq(msg), isNull(),
                eq(NotificationPreferenceRules.REASON_ALL_UNSUBSCRIBED));
        // 退订不是故障：不得落入 FAILED 记录
        verify(recordRecorder, never()).record(any(), any(), anyInt(), eq(false), any());
    }

    @Test
    @DisplayName("dispatch：偏好查询异常按 FAILED 记录并向上传播（不静默吞掉）")
    void dispatch_preferenceLookupFailureRecordsFailed() {
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "标题", "正文");
        when(preferenceService.applyPreferences(msg))
                .thenThrow(new IllegalStateException("偏好查询失败"));

        RuntimeException ex = assertThrows(IllegalStateException.class, () -> service.dispatch(msg));

        assertEquals("偏好查询失败", ex.getMessage());
        assertNull(port.last);
        verify(recordRecorder).record(eq(msg), isNull(), eq(0), eq(false), eq("偏好查询失败"));
    }

    @Test
    @DisplayName("sendWithTemplate：模板发送同样经过偏好过滤（模板编码随 SKIPPED 回填）")
    void sendWithTemplate_preferenceHookAppliesWithTemplateCode() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenReturn(template("t", "c", "EMAIL", 1));
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", Map.of(), null, null, null, null, List.of("opted.out@x.com"), null);
        when(preferenceService.applyPreferences(any(NotificationMessage.class))).thenReturn(null);

        int delivered = service.sendWithTemplate(req);

        assertEquals(0, delivered);
        assertNull(port.last);
        verify(recordRecorder).skip(any(NotificationMessage.class), eq("ORDER_SHIPPED"),
                eq(NotificationPreferenceRules.REASON_ALL_UNSUBSCRIBED));
    }

    // ------------------------------------------------------------------
    // M14：免打扰时段挂点（时间维总闸，先于 M13 偏好过滤）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dispatch：定向接收者全部处于免打扰 → 记 SKIPPED、不调适配器、静默返回 0")
    void dispatch_quietHoursSuppressedSkipsPortAndRecordsSkipped() {
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "NOTICE", null, null, null, null, null, List.of("asleep@x.com"), null);
        when(quietHoursService.applyQuietHours(msg)).thenReturn(null);

        int delivered = service.dispatch(msg);

        assertEquals(0, delivered);
        assertNull(port.last);
        verify(recordRecorder).skip(eq(msg), isNull(),
                eq(NotificationQuietHoursRules.REASON_QUIET_HOURS));
        // 抑制不是故障：不得落入 FAILED 记录
        verify(recordRecorder, never()).record(any(), any(), anyInt(), eq(false), any());
    }

    @Test
    @DisplayName("dispatch：免打扰抑制时不再进入偏好过滤（总闸短路，偏好无查询必要）")
    void dispatch_quietHoursShortCircuitsBeforePreferences() {
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "NOTICE", null, null, null, null, null, List.of("asleep@x.com"), null);
        when(quietHoursService.applyQuietHours(msg)).thenReturn(null);

        service.dispatch(msg);

        verify(preferenceService, never()).applyPreferences(any(NotificationMessage.class));
    }

    @Test
    @DisplayName("dispatch：免打扰放行后的消息才进入偏好过滤，记录仍以原始消息为审计对象")
    void dispatch_quietHoursFilteredMessageFlowsToPreferences() {
        NotificationMessage original = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "NOTICE", null, null, null, null, null, List.of("a@x.com", "b@x.com"), null);
        NotificationMessage afterQuiet = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "NOTICE", null, null, null, null, null, List.of("a@x.com"), null);
        when(quietHoursService.applyQuietHours(original)).thenReturn(afterQuiet);

        int delivered = service.dispatch(original);

        assertEquals(1, delivered);
        assertSame(afterQuiet, port.last);
        verify(preferenceService).applyPreferences(afterQuiet);
        verify(recordRecorder).record(eq(original), isNull(), eq(1), eq(true), isNull());
    }

    @Test
    @DisplayName("dispatch：免打扰查询异常按 FAILED 记录并向上传播（不静默吞掉）")
    void dispatch_quietHoursLookupFailureRecordsFailed() {
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "标题", "正文");
        when(quietHoursService.applyQuietHours(msg))
                .thenThrow(new IllegalStateException("免打扰查询失败"));

        RuntimeException ex = assertThrows(IllegalStateException.class, () -> service.dispatch(msg));

        assertEquals("免打扰查询失败", ex.getMessage());
        assertNull(port.last);
        verify(recordRecorder).record(eq(msg), isNull(), eq(0), eq(false), eq("免打扰查询失败"));
        // 异常发生在偏好过滤之前：偏好服务不应被调用
        verify(preferenceService, never()).applyPreferences(any(NotificationMessage.class));
    }

    @Test
    @DisplayName("sendWithTemplate：模板发送同样经过免打扰过滤（模板编码随 SKIPPED 回填）")
    void sendWithTemplate_quietHoursHookAppliesWithTemplateCode() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenReturn(template("t", "c", "EMAIL", 1));
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", Map.of(), null, null, null, null, List.of("asleep@x.com"), null);
        when(quietHoursService.applyQuietHours(any(NotificationMessage.class))).thenReturn(null);

        int delivered = service.sendWithTemplate(req);

        assertEquals(0, delivered);
        assertNull(port.last);
        verify(recordRecorder).skip(any(NotificationMessage.class), eq("ORDER_SHIPPED"),
                eq(NotificationQuietHoursRules.REASON_QUIET_HOURS));
    }

    // ------------------------------------------------------------------
    // M16：有界重试与故障转移
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dispatch：瞬时故障重试后成功，记录 SUCCESS（重试不改变终态契约）")
    void dispatch_retriesTransientFailureThenSucceeds() {
        port.failTimes = 2; // 前两次瞬时故障，第三次成功

        int delivered = service.dispatch(new NotificationMessage(Channel.EMAIL, "标题", "正文"));

        assertEquals(1, delivered);
        assertEquals(3, port.calls);
        verify(recordRecorder).record(any(NotificationMessage.class), isNull(), eq(1), eq(true), isNull());
    }

    @Test
    @DisplayName("dispatch：业务确定性拒绝不重试（BusinessException 原样上抛）")
    void dispatch_doesNotRetryBusinessException() {
        BusinessException denied = new BusinessException(
                CommonErrorCode.VALIDATION_ERROR, "邮件通知渠道未启用");
        port.failWith = denied;

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.dispatch(new NotificationMessage(Channel.EMAIL, "标题", "正文")));

        assertSame(denied, ex);
        assertEquals(1, port.calls);
        verify(recordRecorder).record(any(NotificationMessage.class), isNull(), eq(0), eq(false),
                eq("邮件通知渠道未启用"));
    }

    @Test
    @DisplayName("dispatch：瞬时故障耗尽最大次数后原样上抛并记 FAILED")
    void dispatch_givesUpAfterMaxAttempts() {
        port.failWith = new IllegalStateException("网关持续故障");

        RuntimeException ex = assertThrows(IllegalStateException.class,
                () -> service.dispatch(new NotificationMessage(Channel.EMAIL, "标题", "正文")));

        assertEquals("网关持续故障", ex.getMessage());
        assertEquals(3, port.calls); // 默认 max-attempts=3，含首次
        verify(recordRecorder).record(any(NotificationMessage.class), isNull(), eq(0), eq(false),
                eq("网关持续故障"));
    }

    @Test
    @DisplayName("dispatch：重试关闭时只尝试一次")
    void dispatch_noRetryWhenDisabled() {
        retryProperties.setEnabled(false);
        port.failWith = new IllegalStateException("网关持续故障");

        assertThrows(IllegalStateException.class,
                () -> service.dispatch(new NotificationMessage(Channel.EMAIL, "标题", "正文")));

        assertEquals(1, port.calls);
    }

    @Test
    @DisplayName("dispatch：max-attempts 配置被遵守（含 <1 钳制为 1）")
    void dispatch_maxAttemptsConfigured() {
        retryProperties.setMaxAttempts(5);
        port.failWith = new IllegalStateException("网关持续故障");

        assertThrows(IllegalStateException.class,
                () -> service.dispatch(new NotificationMessage(Channel.EMAIL, "标题", "正文")));

        assertEquals(5, port.calls);
    }

    @Test
    @DisplayName("dispatch：开启故障转移后主适配器彻底失败即切备，备成功记 SUCCESS")
    void dispatch_failoverToBackupPortWhenEnabled() {
        failoverProperties.setEnabled(true);
        CapturingPort primary = new CapturingPort();
        primary.failWith = new IllegalStateException("主网关故障");
        CapturingPort backup = new CapturingPort();
        backup.ret = 2;
        NotificationService multi = new NotificationService(List.of(primary, backup),
                templateService, recordRecorder, preferenceService, quietHoursService,
                retryProperties, failoverProperties, digestService);
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "标题", "正文");

        int delivered = multi.dispatch(msg);

        assertEquals(2, delivered);
        assertEquals(3, primary.calls); // 主适配器自身重试耗尽
        assertEquals(1, backup.calls);
        assertSame(msg, backup.last);   // 备适配器收到同一消息
        verify(recordRecorder).record(eq(msg), isNull(), eq(2), eq(true), isNull());
    }

    @Test
    @DisplayName("dispatch：故障转移关闭时不切备（保持单适配器历史行为）")
    void dispatch_noFailoverWhenDisabled() {
        CapturingPort primary = new CapturingPort();
        primary.failWith = new IllegalStateException("主网关故障");
        CapturingPort backup = new CapturingPort();
        NotificationService multi = new NotificationService(List.of(primary, backup),
                templateService, recordRecorder, preferenceService, quietHoursService,
                retryProperties, failoverProperties, digestService);

        assertThrows(IllegalStateException.class,
                () -> multi.dispatch(new NotificationMessage(Channel.EMAIL, "标题", "正文")));

        assertEquals(3, primary.calls);
        assertEquals(0, backup.calls);
    }

    @Test
    @DisplayName("dispatch：主备均失败时上抛最后一个异常并记 FAILED")
    void dispatch_failoverAllFailThrowsLast() {
        failoverProperties.setEnabled(true);
        CapturingPort primary = new CapturingPort();
        primary.failWith = new IllegalStateException("主网关故障");
        CapturingPort backup = new CapturingPort();
        backup.failWith = new IllegalStateException("备网关故障");
        NotificationService multi = new NotificationService(List.of(primary, backup),
                templateService, recordRecorder, preferenceService, quietHoursService,
                retryProperties, failoverProperties, digestService);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> multi.dispatch(new NotificationMessage(Channel.EMAIL, "标题", "正文")));

        assertEquals("备网关故障", ex.getMessage());
        verify(recordRecorder).record(any(NotificationMessage.class), isNull(), eq(0), eq(false),
                eq("备网关故障"));
    }

    @Test
    @DisplayName("dispatch：开启故障转移但渠道无任何适配器时仍显式失败（不静默丢弃）")
    void dispatch_failoverWithoutAnyPortStillFails() {
        failoverProperties.setEnabled(true);
        NotificationService empty = new NotificationService(List.of(),
                templateService, recordRecorder, preferenceService, quietHoursService,
                retryProperties, failoverProperties, digestService);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> empty.dispatch(new NotificationMessage(Channel.EMAIL, "标题", "正文")));

        assertTrue(ex.getMessage().contains("不支持的通知渠道"));
        verify(recordRecorder).record(any(NotificationMessage.class), isNull(), eq(0), eq(false),
                contains("不支持的通知渠道"));
    }

    // ------------------------------------------------------------------
    // M17：聚合摘要挂点（M14/M13 过滤之后、实际路由之前）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dispatch：命中聚合配置 → 记 DIGEST_PENDING、不调适配器、静默返回 0")
    void dispatch_digestedRecordsPendingAndSkipsPort() {
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "NOTICE", null, null, null, null, null, List.of("a@x.com"), null);
        when(digestService.enqueue(msg)).thenReturn(new EnqueueResult(true, List.of()));

        int delivered = service.dispatch(msg);

        assertEquals(0, delivered);
        assertNull(port.last);
        // 原消息未投递：记 DIGEST_PENDING（聚合中是独立生命周期态，不计失败）
        verify(recordRecorder).pending(eq(msg), isNull(),
                eq(NotificationDigestRules.REASON_DIGEST_PENDING));
        // 不得落终态记录或跳过记录
        verify(recordRecorder, never()).record(any(), any(), anyInt(), eq(true), any());
        verify(recordRecorder, never()).record(any(), any(), anyInt(), eq(false), any());
        verify(recordRecorder, never()).skip(any(), any(), any());
    }

    @Test
    @DisplayName("dispatch：合并达阈值 → 摘要消息递归走完整 dispatch 投递，行按一次性语义删除")
    void dispatch_digestThresholdFlushesDigestMessage() {
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "NOTICE", null, null, null, null, null, List.of("a@x.com"), null);
        SysNotificationDigestDO row = digestRow(99L, 7L);
        NotificationMessage digestMsg = new NotificationMessage(Channel.EMAIL, "您有 5 条新通知",
                "摘要正文", NotificationDigestRules.DIGEST_MSG_TYPE, null, null, null, null, null,
                List.of("a@x.com"), null);
        // 摘要消息 msgType=DIGEST 不在聚合配置内：不递归挂起，直通投递（防无限递归）
        when(digestService.enqueue(any(NotificationMessage.class))).thenAnswer(inv -> {
            NotificationMessage m = inv.getArgument(0);
            return NotificationDigestRules.DIGEST_MSG_TYPE.equals(m.msgType())
                    ? NotificationDigestService.NOT_DIGESTED
                    : new EnqueueResult(true, List.of(row));
        });
        when(digestService.buildDigestMessage(row)).thenReturn(digestMsg);

        int delivered = service.dispatch(msg);

        assertEquals(0, delivered); // 原消息被聚合吞并，挂点口径返 0
        assertSame(digestMsg, port.last); // 摘要消息经同一适配器投递
        assertEquals("DIGEST", port.last.msgType());
        // 原消息记 DIGEST_PENDING
        verify(recordRecorder).pending(eq(msg), isNull(),
                eq(NotificationDigestRules.REASON_DIGEST_PENDING));
        // 摘要消息自身也走 M12 留痕（全链路复用）
        verify(recordRecorder).record(eq(digestMsg), isNull(), eq(1), eq(true), isNull());
        // 一次性语义：投递后删行
        verify(digestService).delete(99L);
    }

    @Test
    @DisplayName("dispatch：摘要投递异常仅 warn 兜底，原消息仍返 0 且行仍删（防囤积）")
    void dispatch_digestFlushFailureStillDeletesRow() {
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "NOTICE", null, null, null, null, null, List.of("a@x.com"), null);
        SysNotificationDigestDO row = digestRow(99L, 7L);
        when(digestService.enqueue(any(NotificationMessage.class)))
                .thenReturn(new EnqueueResult(true, List.of(row)));
        when(digestService.buildDigestMessage(row))
                .thenThrow(new IllegalStateException("摘要渲染失败"));

        int delivered = service.dispatch(msg);

        assertEquals(0, delivered);
        assertNull(port.last);
        verify(recordRecorder).pending(eq(msg), isNull(),
                eq(NotificationDigestRules.REASON_DIGEST_PENDING));
        // 一次性语义：异常也删行（聚合缓冲不囤积）
        verify(digestService).delete(99L);
        // 原消息结论不受摘要投递影响：不得记 FAILED
        verify(recordRecorder, never()).record(any(), any(), anyInt(), eq(false), any());
    }

    @Test
    @DisplayName("dispatch：摘要无处可投（buildDigestMessage 返 null）→ 不投递、删行、原消息返 0")
    void dispatch_digestFlushNullMessageDeletesRow() {
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "NOTICE", null, null, null, null, null, List.of("a@x.com"), null);
        SysNotificationDigestDO row = digestRow(99L, 7L);
        when(digestService.enqueue(any(NotificationMessage.class)))
                .thenReturn(new EnqueueResult(true, List.of(row)));
        when(digestService.buildDigestMessage(row)).thenReturn(null);

        int delivered = service.dispatch(msg);

        assertEquals(0, delivered);
        assertNull(port.last);
        verify(digestService).delete(99L);
    }

    @Test
    @DisplayName("dispatch：聚合未命中（NOT_DIGESTED）→ 直通投递，与 M16 及以前行为一致")
    void dispatch_digestDisabledPassesThrough() {
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "标题", "正文");
        when(digestService.enqueue(any(NotificationMessage.class)))
                .thenReturn(NotificationDigestService.NOT_DIGESTED);

        int delivered = service.dispatch(msg);

        assertEquals(1, delivered);
        verify(recordRecorder).record(any(NotificationMessage.class), isNull(), eq(1), eq(true), isNull());
        verify(recordRecorder, never()).pending(any(), any(), any());
    }
}
