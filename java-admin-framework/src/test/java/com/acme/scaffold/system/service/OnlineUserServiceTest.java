package com.acme.scaffold.system.service;

import com.acme.scaffold.security.jwt.JwtProperties;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.security.token.RefreshTokenService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 在线用户服务单测：仅覆盖无 DB 访问的参数守卫路径。
 *
 * <p>DB 交互路径（登记/分页/强制下线/限制/解除）按项目约定由 MySQL 集成测试
 * {@code OnlineUserIT} 覆盖——mock jOOQ 链式 API 既脆弱又偏离真实实现。
 */
@ExtendWith(MockitoExtension.class)
class OnlineUserServiceTest {

    @Mock
    private DSLContext dsl;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private SecurityContextFacade securityContextFacade;
    @Mock
    private JwtProperties jwtProperties;

    private OnlineUserService newService() {
        return new OnlineUserService(dsl, refreshTokenService, securityContextFacade, jwtProperties);
    }

    // ---------- touch 心跳守卫 ----------

    @Test
    void touch_skipsWhenUserIdNull() {
        OnlineUserService svc = newService();
        svc.touch(null, "device-1");
        // 守卫命中不应触达任何 DB 更新
        verifyNoInteractions(dsl);
    }

    @Test
    void touch_skipsWhenDeviceIdNull() {
        OnlineUserService svc = newService();
        svc.touch(1L, null);
        verifyNoInteractions(dsl);
    }

    @Test
    void touch_skipsWhenDeviceIdBlank() {
        OnlineUserService svc = newService();
        svc.touch(1L, "   ");
        verifyNoInteractions(dsl);
    }

    // ---------- sessionState 守卫（fail-open 语义） ----------

    @Test
    void sessionState_returnsNullWhenUserIdNull() {
        // 无法定位会话行 → null 由过滤器按 fail-open 放行（历史令牌兼容）
        assertNull(newService().sessionState(null, "device-1"));
    }

    @Test
    void sessionState_returnsNullWhenDeviceIdBlank() {
        assertNull(newService().sessionState(1L, " "));
    }

    // ---------- markOfflineByUser / release 守卫 ----------

    @Test
    void markOfflineByUser_returnsZeroWhenUserIdNull() {
        assertEquals(0, newService().markOfflineByUser(null, OnlineUserService.REASON_LOGOUT));
        verifyNoInteractions(dsl);
    }

    @Test
    void release_returnsZeroWhenUserIdNull() {
        assertEquals(0, newService().release(null));
        verifyNoInteractions(dsl);
    }
}
