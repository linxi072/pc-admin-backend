package com.acme.scaffold.integration.auth;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.CreateUserRequest;
import com.acme.scaffold.system.dto.ResetPasswordRequest;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 「忘记密码 / 修改密码 / 管理员重置」集成测试：验证密码链路全闭环。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>忘记密码闭环（手机号核验 / 邮箱核验）：重置后新密码可登录、旧密码被拒；</li>
 *   <li>防账号枚举：用户不存在与验证信息不匹配返回<b>同一</b> 401（AUTH_001）；
 *       未提供任何验证凭据返回 400；</li>
 *   <li>重置后旧凭证全部失效（token_version 递增 + 刷新令牌吊销），旧 accessToken 调接口 401；</li>
 *   <li><b>修复验证</b>：管理员重置密码后用户可直接登录（历史缺陷：resetPassword 置
 *       password_expired=1 导致 423 AUTH_014 拦截且无自助入口，形成死锁）。</li>
 * </ul>
 *
 * <p>运行前提（本机 / CI，不使用 Docker）：原生 MySQL 8.x，库名 java_admin，
 * Flyway 自动执行全部迁移；admin/admin123 属于 SUPER_ADMIN。
 *
 * <p><b>为何裸请求走 {@link java.net.http.HttpClient}</b>：TestRestTemplate 底层
 * HttpURLConnection 在收到 401（带 WWW-Authenticate）时会尝试认证重试，streaming
 * 模式下直接抛 {@code HttpRetryException}，测试拿不到错误响应体；本类大量断言
 * 401/423 错误体，故「预期失败」的请求统一改走 JDK HttpClient。
 */
class ForgotPasswordIT extends AbstractHttpIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /** 测试用户句柄：id 与 username 必须同源（历史坑：两边各自 nanoTime 导致对不上）。 */
    private record TestUser(Long id, String username) {
    }

    @Autowired
    private DSLContext dsl;

    @Autowired
    private UserService userService;

    /** 本类创建的测试用户，@AfterEach 经删除接口清理，避免脏数据累积。 */
    private final List<Long> createdUserIds = new ArrayList<>();

    /**
     * 认证类请求序号：每次变换 IP 末段。
     *
     * <p>背景：{@code RateLimitFilter} 对 login / refresh / forgot-password 按 IP
     * 60s 窗口限流 10 次且过滤器为单例，同一测试类重复运行（如修复后重跑）时计数
     * 跨运行累计会误触 429；与 {@code clientIp()} 的类级隔离同思路，这里细化到调用级。
     */
    private final AtomicInteger authSeq = new AtomicInteger();

    private String nextAuthIp() {
        String base = clientIp();
        int octet = authSeq.incrementAndGet() % 250 + 1;
        return base.substring(0, base.lastIndexOf('.') + 1) + octet;
    }

    private String cachedToken;

    private String token() {
        if (cachedToken == null) {
            cachedToken = login("admin", "admin123");
        }
        return cachedToken;
    }

    @AfterEach
    void cleanup() {
        for (Long uid : createdUserIds) {
            rest.exchange(baseUrl() + "/api/system/users/" + uid, HttpMethod.DELETE,
                    new HttpEntity<>(bearer(token())), Result.class);
        }
        createdUserIds.clear();
    }

    /** 裸响应：HTTP 状态码 + 解析后的业务码/消息 + 原始 body（成功场景供取 data）。 */
    private record RawResponse(int status, String code, String message, String body) {
        boolean isSuccess() {
            return status == 200 && "0".equals(code);
        }

        JsonNode data() throws Exception {
            return MAPPER.readTree(body).path("data");
        }
    }

    /** POST JSON（裸请求，供断言失败响应体）；IP 每次变换以隔离登录/找回限流。 */
    private RawResponse postJson(String path, Map<String, ?> body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", nextAuthIp())
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode node = MAPPER.readTree(resp.body());
        return new RawResponse(resp.statusCode(),
                node.path("code").asText(null),
                node.path("message").asText(null),
                resp.body());
    }

    /** GET（带 Bearer，裸请求）；供「旧令牌应失效」等预期 401 场景。 */
    private RawResponse getJson(String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Authorization", "Bearer " + token)
                .header("X-Device-Id", "it-forgot-device")
                .header("X-Forwarded-For", nextAuthIp())
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode node = MAPPER.readTree(resp.body());
        return new RawResponse(resp.statusCode(),
                node.path("code").asText(null),
                node.path("message").asText(null),
                resp.body());
    }

    /** 裸登录尝试。 */
    private RawResponse attemptLogin(String username, String password) throws Exception {
        return postJson("/api/auth/login", Map.of("username", username, "password", password));
    }

    /** 裸忘记密码尝试。 */
    private RawResponse attemptForgot(String username, String newPassword, String mobile, String email)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("newPassword", newPassword);
        if (mobile != null) {
            body.put("mobile", mobile);
        }
        if (email != null) {
            body.put("email", email);
        }
        return postJson("/api/auth/forgot-password", body);
    }

    /**
     * 创建测试用户（唯一用户名 + 登记手机号/邮箱），返回 id 与 username 句柄。
     * 口令固定为满足强度策略的夹具值 {@code Password123!}。
     *
     * <p>username 在<b>本方法内</b>生成并随句柄返回：调用方不得自行再拼一个
     * （早前各自 {@code System.nanoTime()} 拼出两个不同用户名，导致「库里有的查不到、
     * 查不到的偏要登」的全量 401 假象）。
     */
    private TestUser createTestUser(String tag, String mobile, String email) {
        List<SysRoleDO> roles = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ROLE));
        assertFalse(roles.isEmpty(), "库中应存在角色种子");
        List<SysOrgDO> orgs = JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ORG));
        assertFalse(orgs.isEmpty(), "库中应存在部门种子");
        String uniq = "it-forgot-" + tag + "-" + System.nanoTime();
        Long uid = userService.create(new CreateUserRequest(
                uniq, "Password123!", "忘记密码测试 " + tag, mobile, email,
                List.of(roles.get(0).getId()), null, List.of(orgs.get(0).getId()), null));
        createdUserIds.add(uid);
        return new TestUser(uid, uniq);
    }

    // ==================== 忘记密码：闭环 ====================

    @Test
    @DisplayName("忘记密码闭环（手机号核验）：重置后新密码可登录、旧密码被拒")
    void forgotPassword_mobileVerification_fullLoop() throws Exception {
        String mobile = "138" + String.format("%08d", System.nanoTime() % 100_000_000L);
        TestUser user = createTestUser("mobile", mobile, null);

        // 忘记密码：手机号匹配 → 重置成功
        RawResponse resetResp = attemptForgot(user.username(), "NewPass456!", mobile, null);
        assertEquals(HttpStatus.OK.value(), resetResp.status(), "手机号核验通过应重置成功");
        assertEquals(true, resetResp.isSuccess());

        // 新密码可登录
        RawResponse loginNew = attemptLogin(user.username(), "NewPass456!");
        assertEquals(HttpStatus.OK.value(), loginNew.status(), "重置后新密码应可登录");
        assertNotNull(loginNew.data().get("accessToken"), "登录响应应含 accessToken");

        // 旧密码被拒（401 AUTH_001，与「密码错误」同一错误，不泄漏重置细节）
        RawResponse loginOld = attemptLogin(user.username(), "Password123!");
        assertEquals(HttpStatus.UNAUTHORIZED.value(), loginOld.status(), "旧密码应被拒");
        assertEquals("AUTH_001", loginOld.code());
    }

    @Test
    @DisplayName("忘记密码闭环（邮箱核验）：重置后新密码可登录")
    void forgotPassword_emailVerification_fullLoop() throws Exception {
        String email = "it-forgot-" + System.nanoTime() + "@example.com";
        TestUser user = createTestUser("email", null, email);

        RawResponse resetResp = attemptForgot(user.username(), "NewPass456!", null, email);
        assertEquals(HttpStatus.OK.value(), resetResp.status(), "邮箱核验通过应重置成功");

        RawResponse loginNew = attemptLogin(user.username(), "NewPass456!");
        assertEquals(HttpStatus.OK.value(), loginNew.status(), "重置后新密码应可登录");
        assertNotNull(loginNew.data().get("accessToken"));
    }

    @Test
    @DisplayName("重置后旧凭证全部失效：旧 accessToken 调接口 401")
    void forgotPassword_revokesOldCredentials() throws Exception {
        String mobile = "139" + String.format("%08d", System.nanoTime() % 100_000_000L);
        TestUser user = createTestUser("revoke", mobile, null);

        // 先登录拿到有效令牌（create 置 password_expired=1，直接清除以便登录）
        dsl.update(JooqTables.SYS_USER.table())
                .set(JooqTables.SYS_USER.field("password_expired", Integer.class), 0)
                .where(JooqTables.SYS_USER.field("id", Long.class).eq(user.id()))
                .execute();
        String oldToken = login(user.username(), "Password123!");
        assertNotNull(oldToken);

        // 忘记密码重置 → token_version 递增 + 刷新令牌吊销 + 在线会话置离线
        RawResponse resetResp = attemptForgot(user.username(), "NewPass456!", mobile, null);
        assertEquals(HttpStatus.OK.value(), resetResp.status());

        // 旧令牌访问受保护接口 → 401（凭证已失效）
        RawResponse meResp = getJson("/api/system/users/" + user.id(), oldToken);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), meResp.status(), "重置后旧令牌应失效");
    }

    // ==================== 忘记密码：守卫 ====================

    @Test
    @DisplayName("防账号枚举：用户不存在与验证信息不匹配返回同一 401")
    void forgotPassword_antiEnumeration_sameError() throws Exception {
        // 场景 A：真实用户 + 错误手机号
        String mobile = "137" + String.format("%08d", System.nanoTime() % 100_000_000L);
        TestUser user = createTestUser("enum", mobile, null);
        RawResponse mismatch = attemptForgot(user.username(), "NewPass456!", "13700000000", null);

        // 场景 B：不存在的用户名 + 任意手机号
        RawResponse notExist = attemptForgot("it-forgot-no-such-user-" + System.nanoTime(),
                "NewPass456!", "13700000000", null);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), mismatch.status());
        assertEquals(HttpStatus.UNAUTHORIZED.value(), notExist.status());
        // 同一错误码 + 同一消息：攻击者无法通过差异提示枚举有效账号
        assertEquals("AUTH_001", mismatch.code());
        assertEquals("AUTH_001", notExist.code());
        assertEquals(mismatch.message(), notExist.message());

        // 且原口令未被改动：用原密码登录仍因 password_expired=1 被 423 拦截
        //（若重置误生效则会是 401 密码错误，以此区分「未改动」与「改错了」）
        RawResponse loginOld = attemptLogin(user.username(), "Password123!");
        assertEquals(HttpStatus.LOCKED.value(), loginOld.status(),
                "未通过核验不得重置口令：原口令仍生效（新用户仍被 423 拦截）");
        assertEquals("AUTH_014", loginOld.code());
    }

    @Test
    @DisplayName("未提供任何验证凭据：400 参数校验")
    void forgotPassword_noVerifier_400() throws Exception {
        TestUser user = createTestUser("noverify", "13600000001", null);

        RawResponse resp = attemptForgot(user.username(), "NewPass456!", null, null);
        assertEquals(HttpStatus.BAD_REQUEST.value(), resp.status(), "未提供手机号/邮箱应被服务端拒绝");
    }

    @Test
    @DisplayName("忘记密码禁止复用最近 5 次密码：422")
    void forgotPassword_rejectsReusedPassword() throws Exception {
        String mobile = "135" + String.format("%08d", System.nanoTime() % 100_000_000L);
        TestUser user = createTestUser("reuse", mobile, null);

        // 新密码与当前口令相同 → 触发历史复用校验（create 已把初始口令写入密码历史）
        RawResponse resp = attemptForgot(user.username(), "Password123!", mobile, null);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), resp.status(), "复用当前口令应被拒");
        assertEquals("AUTH_007", resp.code());
    }

    // ==================== 管理员重置：修复验证 ====================

    @Test
    @DisplayName("修复验证：管理员重置密码后用户可直接登录（不再 423 AUTH_014 死锁）")
    void adminResetPassword_thenLoginDirectly() throws Exception {
        TestUser user = createTestUser("adminreset", null, null);

        // 管理员重置密码（历史缺陷：resetPassword 置 password_expired=1，重置后登录被
        // 423 拦截且登录页无自助改密入口，用户卡死）
        ResponseEntity<Result> resetResp = post("/api/system/users/" + user.id() + "/reset-password",
                new ResetPasswordRequest("ResetPass789!"), Result.class, token());
        assertEquals(HttpStatus.OK.value(), resetResp.getStatusCode().value());
        assertEquals(true, resetResp.getBody().isSuccess());

        // 修复后：新密码直接登录成功（200），不再被 AUTH_014 拦截
        RawResponse loginResp = attemptLogin(user.username(), "ResetPass789!");
        assertEquals(HttpStatus.OK.value(), loginResp.status(),
                "管理员重置后应可直接登录（password_expired 已随重置清零）");
        assertNotNull(loginResp.data().get("accessToken"));
    }

    @Test
    @DisplayName("首次创建用户仍需强制改密：初始口令登录被 423 拦截（语义保留）")
    void createdUser_stillForcedToChangePassword() throws Exception {
        TestUser user = createTestUser("created", null, null);

        // create 仍置 password_expired=1：初始口令必须由本人更换（与「管理员重置可直接登录」区分）
        RawResponse loginResp = attemptLogin(user.username(), "Password123!");
        assertEquals(HttpStatus.LOCKED.value(), loginResp.status(), "423（LOCKED）");
        assertEquals("AUTH_014", loginResp.code());
    }

    @Test
    @DisplayName("库状态核对：重置后 password_expired=0 且 password_changed_at 已刷新")
    void adminResetPassword_clearsExpiredFlagInDb() {
        TestUser user = createTestUser("dbcheck", null, null);

        post("/api/system/users/" + user.id() + "/reset-password",
                new ResetPasswordRequest("ResetPass789!"), Result.class, token());

        SysUserDO after = JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class, user.id());
        assertNotNull(after);
        assertEquals(0, after.getPasswordExpired(), "重置后 password_expired 应为 0");
        assertNotNull(after.getPasswordChangedAt(), "重置后 password_changed_at 应刷新");
    }
}
