package com.acme.scaffold.security;

import com.acme.scaffold.BaseIntegrationTest;
import com.acme.scaffold.common.api.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
 * <p>HTTP 客户端选型：本类使用 JDK 内置 {@link java.net.http.HttpClient} 而非 TestRestTemplate。
 * 原因：Spring 6.1 的 {@code SimpleClientHttpRequest} 对带 body 的请求<b>总是</b>调用
 * {@code setFixedLengthStreamingMode/setChunkedStreamingMode}（工厂的
 * {@code setOutputStreaming/setBufferRequestBody} 均已弃用且不再生效），JDK
 * {@code HttpURLConnection} 因此恒处于 streaming 模式；此时一旦收到 401 响应即抛
 * {@code HttpRetryException("cannot retry due to server authentication, in streaming mode")}，
 * 错误响应体不可读，导致「错误密码登录返回 401 + AUTH_001」这一契约无法断言。
 * {@code java.net.http.HttpClient}（JDK 11+ 内置，零依赖）无此限制，任意状态码的
 * 响应体均可读取。
 *
 * <p>运行：{@code mvn -o test -Dtest=AuthFlowIT -Dspring.profiles.active=integration
 *   -DTEST_DB_URL=... -DTEST_DB_USER=... -DTEST_DB_PASSWORD=...}
 */
class AuthFlowIT extends BaseIntegrationTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    private ObjectMapper objectMapper;

    /** POST JSON 并返回原始响应（状态码 + 响应体字符串），携带独立客户端 IP 规避跨类登录限流累计。 */
    private HttpResponse<String> postJson(String path, Map<String, String> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", clientIp())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Result<?> parseResult(HttpResponse<String> resp) throws Exception {
        return objectMapper.readValue(resp.body(), Result.class);
    }

    @Test
    void loginSuccessReturnsToken() throws Exception {
        HttpResponse<String> resp = postJson("/api/auth/login",
                Map.of("username", "admin", "password", "admin123"));
        assertEquals(200, resp.statusCode());
        Result<?> r = parseResult(resp);
        assertNotNull(r);
        assertTrue(r.isSuccess(), "登录应成功");
        assertNotNull(r.data(), "应返回令牌");
    }

    @Test
    void loginWrongPasswordReturnsAuth001() throws Exception {
        HttpResponse<String> resp = postJson("/api/auth/login",
                Map.of("username", "admin", "password", "wrong-pass"));
        assertEquals(401, resp.statusCode());
        Result<?> r = parseResult(resp);
        assertNotNull(r);
        assertEquals("AUTH_001", r.code());
        assertFalse(r.isSuccess());
    }

    @Test
    void refreshRotatesToken() throws Exception {
        HttpResponse<String> loginResp = postJson("/api/auth/login",
                Map.of("username", "admin", "password", "admin123"));
        assertEquals(200, loginResp.statusCode());
        Result<?> loginResult = parseResult(loginResp);
        assertNotNull(loginResult.data(), "登录应返回令牌");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) loginResult.data();
        String refreshToken = (String) data.get("refreshToken");
        assertNotNull(refreshToken);

        HttpResponse<String> refreshResp = postJson("/api/auth/refresh",
                Map.of("refreshToken", refreshToken));
        assertEquals(200, refreshResp.statusCode());
        Result<?> refreshResult = parseResult(refreshResp);
        assertNotNull(refreshResult.data(), "刷新应返回新令牌");
        @SuppressWarnings("unchecked")
        Map<String, Object> refreshed = (Map<String, Object>) refreshResult.data();
        String newRefresh = (String) refreshed.get("refreshToken");
        assertNotNull(newRefresh);
        // 刷新令牌轮换：新旧令牌应不同
        assertNotEquals(refreshToken, newRefresh, "刷新令牌应轮换");
    }
}
