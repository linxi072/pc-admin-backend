package com.acme.scaffold.system.notifypref;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.system.notifypref.dto.PreferenceItemRequest;
import com.acme.scaffold.system.notifypref.dto.UpdateNotificationPreferenceRequest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link NotificationPreferenceService} 离线单测：入参校验与「先全额校验再落库」守卫。
 *
 * <p>DB 交互路径（矩阵查询 / upsert / 反查过滤）按项目约定由 MySQL 集成测试覆盖——
 * mock jOOQ 链式 API 既脆弱又偏离真实实现；此处仅固化不触库的校验语义：
 * 渠道/消息类型白名单、空集合守卫、以及校验失败时<b>零数据库交互</b>
 * （避免「前几项写成功、后一项非法」的半成品状态）。归一化语义
 * （msgType 空白 → NOTICE 等）由 {@code NotificationPreferenceRulesTest} 守护。</p>
 */
class NotificationPreferenceServiceTest {

    private SecurityContextFacade securityContextFacade;
    private DSLContext dsl;
    private NotificationPreferenceService service;

    @BeforeEach
    void setUp() {
        securityContextFacade = mock(SecurityContextFacade.class);
        dsl = mock(DSLContext.class);
        when(securityContextFacade.requireCurrentPrincipal())
                .thenReturn(new CurrentPrincipal(1L, "zhangsan", "张三", 0L, "web", 1,
                        Set.of(), Set.of()));
        service = new NotificationPreferenceService(dsl, securityContextFacade);
    }

    private static UpdateNotificationPreferenceRequest request(PreferenceItemRequest... items) {
        return new UpdateNotificationPreferenceRequest(List.of(items));
    }

    @Test
    @DisplayName("update：WEBHOOK 无个人偏好语义，拒绝建行")
    void update_rejectsWebhookChannel() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(
                request(new PreferenceItemRequest("WEBHOOK", "NOTICE", 1))));

        assertTrue(ex.getMessage().contains("不支持设置偏好的渠道"), ex.getMessage());
        // 校验先于任何数据库交互
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("update：未知消息类型拒绝（白名单 NOTICE/ALERT/SYSTEM/CC）")
    void update_rejectsUnknownMsgType() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(
                request(new PreferenceItemRequest("IN_APP", "MARKETING", 1))));

        assertTrue(ex.getMessage().contains("不支持的消息类型"), ex.getMessage());
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("update：空集合守卫（内部调用绕过 Bean Validation 时的第二道闸）")
    void update_rejectsEmptyItems() {
        assertThrows(BusinessException.class,
                () -> service.update(new UpdateNotificationPreferenceRequest(List.of())));
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("update：混合合法/非法项整体拒绝，不写半成品")
    void update_allOrNothingValidation() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(
                request(new PreferenceItemRequest("IN_APP", "NOTICE", 1),
                        new PreferenceItemRequest("SMS", "BOGUS", 0))));

        assertTrue(ex.getMessage().contains("不支持的消息类型"), ex.getMessage());
        // 第一项合法也绝不能落库
        verifyNoInteractions(dsl);
    }
}
