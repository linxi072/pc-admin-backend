package com.acme.scaffold.security;

import com.acme.scaffold.BaseIntegrationTest;
import com.acme.scaffold.common.api.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证流集成测试：登录成功 / 密码错误 / 刷新令牌轮换。
 *
 * <p>运行前提（本机 / CI，不使用 Docker）：
 * <ol>
 *   <li>原生 MySQL 8.x，库名 java_admin，启动时由 Flyway 自动建表；</li>
 *   <li>admin 账号密码为 admin123（由 V4__fix_admin_password.sql 注入）；</li>
 *   <li>启动需带 {@code -Dio.netty.resolver.dns.useJdkResolver=true} 并直连 IPv6 回环，
 *       规避沙箱 Netty DNS 解析失败与本地代理拦截（详见 README §16）。</li>
 * </ol>
 *
 * 运行：{@code mvn -o test -Dtest=AuthFlowIT -Dspring.profiles.active=integration
 *   -DTEST_DB_URL=... -DTEST_DB_USER=... -DTEST_DB_PASSWORD=...}
 */
class AuthFlowIT extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private HttpEntity<Map<String, String>> json(Map<String, String> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    @Test
    void loginSuccessReturnsToken() {
        ResponseEntity<Result> resp = rest.postForEntity(
                baseUrl() + "/api/auth/login",
                json(Map.of("username", "admin", "password", "admin123")),
                Result.class);
        assertEquals(200, resp.getStatusCode().value());
        Result r = resp.getBody();
        assertNotNull(r);
        assertTrue(r.isSuccess(), "登录应成功");
        assertNotNull(r.data(), "应返回令牌");
    }

    @Test
    void loginWrongPasswordReturnsAuth001() {
        ResponseEntity<Result> resp = rest.postForEntity(
                baseUrl() + "/api/auth/login",
                json(Map.of("username", "admin", "password", "wrong-pass")),
                Result.class);
        assertEquals(401, resp.getStatusCode().value());
        Result r = resp.getBody();
        assertNotNull(r);
        assertEquals("AUTH_001", r.code());
        assertFalse(r.isSuccess());
    }

    @Test
    void refreshRotatesToken() {
        ResponseEntity<Map> loginResp = rest.postForEntity(
                baseUrl() + "/api/auth/login",
                json(Map.of("username", "admin", "password", "admin123")),
                Map.class);
        Map<String, Object> data = (Map<String, Object>) loginResp.getBody().get("data");
        String refreshToken = (String) data.get("refreshToken");
        assertNotNull(refreshToken);

        ResponseEntity<Map> refreshResp = rest.postForEntity(
                baseUrl() + "/api/auth/refresh",
                json(Map.of("refreshToken", refreshToken)),
                Map.class);
        assertEquals(200, refreshResp.getStatusCode().value());
        Map<String, Object> refreshed = (Map<String, Object>) refreshResp.getBody().get("data");
        String newRefresh = (String) refreshed.get("refreshToken");
        assertNotNull(newRefresh);
        // 刷新令牌轮换：新旧令牌应不同
        assertNotEquals(refreshToken, newRefresh, "刷新令牌应轮换");
    }
}
