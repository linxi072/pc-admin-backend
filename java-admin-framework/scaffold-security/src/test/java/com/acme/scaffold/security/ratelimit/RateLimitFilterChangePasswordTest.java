package com.acme.scaffold.security.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 认证限流回归：会话无关改密纳入独立 IP 配额， login 计数键保持不变。 */
class RateLimitFilterChangePasswordTest {

    @Test
    void changePasswordRequestsShouldBeLimitedIndependently() throws Exception {
        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.tryAcquire(eq("change-password:203.0.113.9"), eq(10), eq(60)))
                .thenReturn(true, true, true, true, true, true, true, true, true, true, false);
        RateLimitFilter filter = new RateLimitFilter(limiter, new ClientIpResolver(0));
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (req, res) -> passed.incrementAndGet();

        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("/api/auth/change-password"), response, chain);
            assertEquals(200, response.getStatus());
        }
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/change-password"), rejected, chain);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), rejected.getStatus());
        assertEquals(10, passed.get());
        verify(limiter, times(11)).tryAcquire(eq("change-password:203.0.113.9"), eq(10), eq(60));
    }

    @Test
    void loginShouldKeepOriginalRateLimitKey() throws Exception {
        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.tryAcquire(eq("login:203.0.113.9"), eq(10), eq(60))).thenReturn(true);
        RateLimitFilter filter = new RateLimitFilter(limiter, new ClientIpResolver(0));
        AtomicInteger passed = new AtomicInteger();

        filter.doFilter(request("/api/auth/login"), new MockHttpServletResponse(),
                (req, res) -> passed.incrementAndGet());

        assertEquals(1, passed.get());
        verify(limiter).tryAcquire(eq("login:203.0.113.9"), eq(10), eq(60));
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("203.0.113.9");
        return request;
    }
}
