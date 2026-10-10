package com.acme.scaffold.integration;

import com.acme.scaffold.BaseIntegrationTest;
import com.acme.scaffold.common.api.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * 集成测试 HTTP 工具基类。
 *
 * <p>继承 {@link BaseIntegrationTest}（拉起真实 Spring 上下文 + 随机端口 +
 * {@code integration} profile + 本机 MySQL），封装「登录拿令牌 → 带 Bearer 调接口」的
 * 通用链路，使各业务集成测试聚焦于场景编排与断言，而非重复的鉴权样板。
 *
 * <p>鉴权链路：{@code /api/auth/login}（白名单接口，无需令牌）→ 取得 {@code accessToken}
 * → 后续接口经由 {@code Authorization: Bearer} 头携带，由 {@code JwtAuthConverter} 还原
 * {@code CurrentPrincipal}，从而验证「认证 → 授权 → 业务 → 数据落库」全链路。
 */
public abstract class AbstractHttpIntegrationTest extends BaseIntegrationTest {

    @Autowired
    protected TestRestTemplate rest;

    /** 构造带 Bearer 令牌的 JSON 请求头（token 为 null 时不附加令牌，用于未认证场景）。 */
    protected HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        return h;
    }

    /** 以用户名/密码登录，返回 accessToken；登录失败抛 {@link IllegalStateException}。 */
    protected String login(String username, String password) {
        Map<String, String> body = Map.of("username", username, "password", password);
        ResponseEntity<Result> resp = rest.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(body, bearer(null)),
                Result.class);
        if (resp.getStatusCode().value() != 200 || resp.getBody() == null || !resp.getBody().isSuccess()) {
            throw new IllegalStateException("登录失败: " + resp.getStatusCode());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getBody().data();
        return (String) data.get("accessToken");
    }

    @SuppressWarnings("unchecked")
    protected <T> ResponseEntity<T> authed(HttpMethod method, String path, Object body, Class<T> type, String token) {
        return rest.exchange(baseUrl() + path, method, new HttpEntity<>(body, bearer(token)), type);
    }

    protected <T> ResponseEntity<T> get(String path, Class<T> type, String token) {
        return authed(HttpMethod.GET, path, null, type, token);
    }

    protected <T> ResponseEntity<T> post(String path, Object body, Class<T> type, String token) {
        return authed(HttpMethod.POST, path, body, type, token);
    }
}
