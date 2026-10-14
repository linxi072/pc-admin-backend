package com.acme.scaffold.integration.auth;

import com.acme.scaffold.BaseIntegrationTest;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.CreateUserRequest;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证会话异常场景集成测试：登出吊销、改密全局失效、未认证拦截、刷新令牌校验。
 *
 * <p>HTTP 客户端选型同 {@code AuthFlowIT}：Spring 6.1 TestRestTemplate 对带 body 请求恒处于
 * streaming 模式，收到 401 即抛 {@code HttpRetryException} 导致错误响应体不可读；
 * 本类大量断言 401 响应体（错误码 AUTH_004/AUTH_005），故全程使用 JDK 内置
 * {@link java.net.http.HttpClient}。
 *
 * <p>运行前提：本机 MySQL（root/root），admin/admin123 属 SUPER_ADMIN，激活 integration profile。
 */
class AuthSessionIT extends BaseIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private org.jooq.DSLContext dsl;

    private final List<Long> createdUserIds = new java.util.ArrayList<>();

    /** 登录尝试序号：每次变换 IP 末段，规避 RateLimitFilter（login:IP 10 次/60s）跨运行累计。 */
    private final AtomicInteger loginSeq = new AtomicInteger();

    private String nextLoginIp() {
        String base = clientIp();
        int octet = loginSeq.incrementAndGet() % 250 + 1;
        return base.substring(0, base.lastIndexOf('.') + 1) + octet;
    }

    @AfterEach
    void cleanup() {
        for (Long uid : createdUserIds) {
            userService.delete(uid);
        }
        createdUserIds.clear();
    }

    private record Tokens(String accessToken, String refreshToken) {
    }

    /** 登录并解析出双令牌；登录失败抛 IllegalStateException。 */
    private Tokens login(String username, String password) throws Exception {
        HttpResponse<String> resp = postJson("/api/auth/login",
                Map.of("username", username, "password", password));
        assertEquals(200, resp.statusCode(), "登录应成功: " + resp.body());
        Result<?> r = objectMapper.readValue(resp.body(), Result.class);
        assertNotNull(r.data(), "登录应返回令牌");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.data();
        return new Tokens((String) data.get("accessToken"), (String) data.get("refreshToken"));
    }

    private HttpResponse<String> postJson(String path, Map<String, ?> body) throws Exception {
        return postJson(path, body, nextLoginIp());
    }

    private HttpResponse<String> postJson(String path, Map<String, ?> body, String ip) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", ip)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getJson(String path, String accessToken) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(baseUrl() + path));
        if (accessToken != null) {
            b.header("Authorization", "Bearer " + accessToken);
        }
        return HTTP.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private Result<?> parse(HttpResponse<String> resp) throws Exception {
        return objectMapper.readValue(resp.body(), Result.class);
    }

    /** 创建测试用户并重置为已知口令（清除首登改密标记），返回用户名。 */
    private String freshUser(String tag, String password) {
        List<SysRoleDO> roles = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ROLE));
        List<SysOrgDO> orgs = JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ORG));
        assertFalse(roles.isEmpty() && orgs.isEmpty(), "库中应存在角色/部门种子");
        String uname = "it-auth-" + tag + "-" + System.nanoTime();
        Long uid = userService.create(new CreateUserRequest(
                uname, "Initial123!", "IT认证-" + tag, null, null,
                List.of(roles.get(0).getId()), null, List.of(orgs.get(0).getId()), null));
        createdUserIds.add(uid);
        // 重置为已知口令（V28 修复后 resetPassword 清除 password_expired，重置后可直接登录）
        userService.resetPassword(uid, password);
        return uname;
    }

    // ==================== 登出与刷新 ====================

    /**
     * 异常：登出后旧刷新令牌立即失效（401 AUTH_005）。
     *
     * <p>错误码说明：{@code RefreshTokenServiceImpl.verify} 对「已吊销令牌被再次使用」
     * 按<b>重放检测</b>处理——吊销整个令牌家族并返回 {@code AUTH_005 TOKEN_REVOKED}
     * （而非 AUTH_004 REFRESH_INVALID，后者保留给「自然过期」）。登出即吊销，
     * 故旧刷新令牌复用命中重放路径，返回 AUTH_005 是刻意设计。
     */
    @Test
    void logoutInvalidatesRefreshToken() throws Exception {
        Tokens tokens = login("admin", "admin123");

        // logout 需登录态：Bearer 头携带 access token
        HttpRequest logout = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/auth/logout"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> logoutReal = HTTP.send(logout, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, logoutReal.statusCode(), "登出应成功");

        HttpResponse<String> refreshResp = postJson("/api/auth/refresh",
                Map.of("refreshToken", tokens.refreshToken()));
        assertEquals(401, refreshResp.statusCode(), "登出后旧刷新令牌应失效");
        Result<?> r = parse(refreshResp);
        assertEquals("AUTH_005", r.code(), "吊销令牌复用按重放检测处理，返回 TOKEN_REVOKED");
    }

    /** 异常：非法刷新令牌 → 401 AUTH_004。 */
    @Test
    void refreshWithGarbageToken_returns401() throws Exception {
        HttpResponse<String> resp = postJson("/api/auth/refresh",
                Map.of("refreshToken", "not-a-real-token"));
        assertEquals(401, resp.statusCode());
        assertEquals("AUTH_004", parse(resp).code());
    }

    // ==================== 改密与凭证失效 ====================

    /**
     * 异常+正常：改密后旧 access token 立即失效（401 AUTH_005），新口令可正常登录。
     *
     * <p>闭环：改密 → token_version 递增 → TokenVersionVerifier 拒绝旧凭证 →
     * 刷新令牌全部吊销 → 新旧口令切换生效。
     */
    @Test
    void changePasswordInvalidatesOldAccessToken() throws Exception {
        String username = freshUser("chgpwd", "Reset123!");
        Tokens before = login(username, "Reset123!");

        HttpResponse<String> changeResp = postJson("/api/auth/change-password",
                Map.of("username", username, "oldPassword", "Reset123!", "newPassword", "Changed123!"));
        assertEquals(200, changeResp.statusCode(), "改密应成功: " + changeResp.body());
        assertTrue(parse(changeResp).isSuccess());

        // 旧 access token 携带旧 token_version → 401 AUTH_005
        HttpResponse<String> oldTokenResp = getJson("/api/profile", before.accessToken());
        assertEquals(401, oldTokenResp.statusCode(), "改密后旧 access token 应失效");
        assertEquals("AUTH_005", parse(oldTokenResp).code());

        // 旧口令登录被拒；新口令登录成功
        HttpResponse<String> oldPwdResp = postJson("/api/auth/login",
                Map.of("username", username, "password", "Reset123!"));
        assertEquals(401, oldPwdResp.statusCode(), "旧口令应被拒绝");
        Tokens after = login(username, "Changed123!");
        assertNotNull(after.accessToken());

        // 旧刷新令牌同样已吊销
        HttpResponse<String> oldRefreshResp = postJson("/api/auth/refresh",
                Map.of("refreshToken", before.refreshToken()));
        assertEquals(401, oldRefreshResp.statusCode(), "改密后旧刷新令牌应吊销");
    }

    // ==================== 未认证拦截 ====================

    /** 异常：未携带令牌访问受保护接口 → 401。 */
    @Test
    void missingToken_returns401() throws Exception {
        HttpResponse<String> resp = getJson("/api/profile", null);
        assertEquals(401, resp.statusCode(), "未认证请求应返回 401");
    }

    /** 异常：伪造令牌 → 401。 */
    @Test
    void forgedToken_returns401() throws Exception {
        HttpResponse<String> resp = getJson("/api/profile", "forged.token.value");
        assertEquals(401, resp.statusCode(), "伪造令牌应返回 401");
    }

    /** 异常：错误密码 → 401 AUTH_001（防枚举统一错误）。 */
    @Test
    void wrongPassword_returnsAuth001() throws Exception {
        HttpResponse<String> resp = postJson("/api/auth/login",
                Map.of("username", "admin", "password", "definitely-wrong"));
        assertEquals(401, resp.statusCode());
        Result<?> r = parse(resp);
        assertEquals("AUTH_001", r.code());
        assertFalse(r.isSuccess());
    }

    /** 异常：用户不存在与密码错误返回同一错误（防账号枚举）。 */
    @Test
    void nonexistentUser_sameErrorAsWrongPassword() throws Exception {
        HttpResponse<String> noSuch = postJson("/api/auth/login",
                Map.of("username", "no-such-user-" + System.nanoTime(), "password", "whatever1"));
        HttpResponse<String> wrongPwd = postJson("/api/auth/login",
                Map.of("username", "admin", "password", "whatever1"));
        assertEquals(noSuch.statusCode(), wrongPwd.statusCode(), "状态码应一致");
        assertEquals(parse(noSuch).code(), parse(wrongPwd).code(), "错误码应一致（防枚举）");
    }
}
