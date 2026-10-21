package com.acme.scaffold.system.log;

import com.acme.scaffold.security.entity.SysLoginLogDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link LoginLogView} 脱敏开关单元测试（纯函数，无需 DB）。
 *
 * <p>锁定两条安全不变量：
 * <ol>
 *   <li>默认（{@code sensitiveVisible=false}）IP 必须掩码——这是防个人轨迹泄漏的最后一道防线；</li>
 *   <li>仅当显式持有敏感权限（{@code sensitiveVisible=true}）时才回传原文；</li>
 * </ol>
 * 以及其它字段（用户名/结果/时间等）在任何情况下都应原样透传，不被误伤。</p>
 */
class LoginLogViewTest {

    private static SysLoginLogDO sample() {
        SysLoginLogDO d = new SysLoginLogDO();
        d.setId(1001L);
        d.setUserId(9L);
        d.setUsername("admin");
        d.setLoginType("PASSWORD");
        d.setResult("SUCCESS");
        d.setFailureCode(null);
        d.setIpAddress("192.168.1.23");
        d.setUserAgent("Mozilla/5.0");
        d.setTraceId("trace-abc");
        d.setOccurredAt(LocalDateTime.of(2026, 10, 16, 9, 30));
        return d;
    }

    @Test
    void masksIpByDefault() {
        LoginLogView v = LoginLogView.of(sample(), false);
        assertEquals("192.168.*.*", v.ipAddress(), "默认必须掩码 IP");
        // 其它字段原样透传
        assertEquals(1001L, v.id());
        assertEquals("admin", v.username());
        assertEquals("PASSWORD", v.loginType());
        assertEquals("SUCCESS", v.result());
        assertEquals("trace-abc", v.traceId());
        assertEquals(LocalDateTime.of(2026, 10, 16, 9, 30), v.occurredAt());
    }

    @Test
    void returnsRawIpWhenSensitiveVisible() {
        LoginLogView v = LoginLogView.of(sample(), true);
        assertEquals("192.168.1.23", v.ipAddress(), "持有敏感权限时回传原文");
    }

    @Test
    void nonIpv4IsFullyMaskedByDefault() {
        SysLoginLogDO d = sample();
        d.setIpAddress("2001:db8::1");
        assertEquals("******", LoginLogView.of(d, false).ipAddress());
    }

    @Test
    void nullSourceYieldsNullView() {
        assertNull(LoginLogView.of(null, false));
        assertNull(LoginLogView.of(null, true));
    }
}
