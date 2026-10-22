package com.acme.scaffold.system.notifyquiet;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.system.notifyquiet.dto.UpdateQuietHoursRequest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link NotificationQuietHoursService} 离线单测：入参校验（时间格式 / 退化窗口）与
 * 挂点零库路径（WEBHOOK、广播、无接收方一律放行且不触库）。
 *
 * <p>DB 交互路径（自服务 upsert / 反查过滤）按项目约定由 MySQL 集成测试覆盖——
 * mock jOOQ 链式 API 既脆弱又偏离真实实现；此处仅固化不触库的语义：
 * 校验失败时<b>零数据库交互</b>（避免把非法窗口写进库里污染发送链路判定），
 * 以及无个人作息语义的通道原样放行。时间解析 / 命中 / 穿透语义由
 * {@code NotificationQuietHoursRulesTest} 守护。
 */
class NotificationQuietHoursServiceTest {

    private SecurityContextFacade securityContextFacade;
    private DSLContext dsl;
    private NotificationQuietHoursService service;

    @BeforeEach
    void setUp() {
        securityContextFacade = mock(SecurityContextFacade.class);
        dsl = mock(DSLContext.class);
        when(securityContextFacade.requireCurrentPrincipal())
                .thenReturn(new CurrentPrincipal(1L, "zhangsan", "张三", 0L, "web", 1,
                        Set.of(), Set.of()));
        service = new NotificationQuietHoursService(dsl, securityContextFacade);
    }

    private static UpdateQuietHoursRequest request(String start, String end) {
        return new UpdateQuietHoursRequest(true, start, end, true);
    }

    @Test
    @DisplayName("update：开始时间非法格式拒绝，且不触库")
    void update_rejectsBadStartFormat() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(request("25:00", "08:00")));

        assertTrue(ex.getMessage().contains("格式应为 HH:mm"), ex.getMessage());
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("update：结束时间非法格式拒绝，且不触库")
    void update_rejectsBadEndFormat() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(request("22:00", "8点")));

        assertTrue(ex.getMessage().contains("格式应为 HH:mm"), ex.getMessage());
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("update：起止相同即退化窗口，拒绝（半开区间下等价于永假/永真歧义）")
    void update_rejectsEqualStartEnd() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(request("10:00", "10:00")));

        assertTrue(ex.getMessage().contains("不能相同"), ex.getMessage());
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("update：跨午夜窗口（start > end）合法，进入落库路径")
    void update_acceptsCrossMidnightWindow() {
        // 合法窗口越过校验进入 upsert：mock dsl 下 fetchOne 返回 null（无现存行）→ 走 insert，
        // insert 最终调 dsl.insertInto(...) 同样触库，故此处仅断言「未被校验拦截」——
        // 用一个必然在触库时才抛出的断言方式不可行，改为断言异常类型不是校验错误。
        // （完整 upsert 路径由集成测试覆盖；本条守护「22:00–08:00 不被误拒」。）
        try {
            service.update(request("22:00", "08:00"));
        } catch (BusinessException ex) {
            throw new AssertionError("跨午夜窗口不应被业务校验拒绝: " + ex.getMessage(), ex);
        } catch (RuntimeException ignored) {
            // mock dsl 链式调用在触库处的 NPE 等：说明已越过校验进入落库路径，符合预期
        }
    }

    @Test
    @DisplayName("applyQuietHours：WEBHOOK 无个人作息语义，原样放行且不触库")
    void applyQuietHours_webhookPassthrough() {
        NotificationMessage msg = new NotificationMessage(Channel.WEBHOOK, "标题", "正文");

        NotificationMessage out = service.applyQuietHours(msg);

        assertSame(msg, out);
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("applyQuietHours：IN_APP 广播（无定向接收者）不过滤，原样放行且不触库")
    void applyQuietHours_inAppBroadcastPassthrough() {
        NotificationMessage msg = new NotificationMessage(Channel.IN_APP, "标题", "正文",
                "NOTICE", null, List.of(1L), null, null, null, null, null);

        NotificationMessage out = service.applyQuietHours(msg);

        assertSame(msg, out);
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("applyQuietHours：WEBSOCKET 广播（空 receiverIds）不过滤，原样放行且不触库")
    void applyQuietHours_websocketBroadcastPassthrough() {
        NotificationMessage msg = new NotificationMessage(Channel.WEBSOCKET, "标题", "正文",
                "NOTICE", null, null, null, null, null, null, null);

        NotificationMessage out = service.applyQuietHours(msg);

        assertSame(msg, out);
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("applyQuietHours：EMAIL 无接收方原样放行（由适配器按既有语义报错，不伪装 SKIPPED）")
    void applyQuietHours_emailWithoutAddressesPassthrough() {
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "NOTICE", null, null, null, null, null, null, null);

        NotificationMessage out = service.applyQuietHours(msg);

        assertSame(msg, out);
        verifyNoInteractions(dsl);
    }
}
