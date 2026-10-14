package com.acme.scaffold.integration.user;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户生命周期业务集成测试：覆盖创建边界（弱口令/重复用户名/重复手机/重复邮箱/缺角色）、
 * 更新边界（非法状态值/手机撞车）、删除后凭证失效。
 *
 * <p>其中「非法状态值 400」「重复邮箱 409」两条断言<b>正确行为</b>（修复后通过），
 * 用于固化以下缺陷的回归：
 * <ul>
 *   <li>D-1：{@code UserService.update} 对 status 无白名单校验，任意字符串可落库，
 *       登录链路只识别 DISABLED/LOCKED，脏状态成为永久数据噪声；</li>
 *   <li>D-2：{@code UserService.create/update} 不做手机/邮箱唯一性预校验——
 *       手机有 DB 唯一索引兜底（409），邮箱无任何约束，同一邮箱可绑定多账号，
 *       与 {@code ProfileService.assertContactAvailable} 的契约相悖。</li>
 * </ul>
 *
 * <p>运行前提：本机 MySQL（root/root），admin/admin123 属 SUPER_ADMIN，激活 integration profile。
 */
class UserLifecycleIT extends AbstractHttpIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<Long> createdUserIds = new java.util.ArrayList<>();

    /** 登录尝试序号：每次变换 IP 末段，规避 RateLimitFilter 跨运行累计（同 OnlineUserIT）。 */
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

    /**
     * 类内缓存 admin 令牌。
     *
     * <p>背景：{@code RateLimitFilter} 按「login:IP」60s 窗口限流 10 次且过滤器为单例，
     * 每个用例都重新登录会快速耗尽配额触发 429。访问令牌有效期足够覆盖单个测试类，
     * 故类内缓存一次（与 RoleManagementIT 同策略）。
     */
    private String cachedToken;

    private String token() {
        if (cachedToken == null) {
            cachedToken = login("admin", "admin123");
        }
        return cachedToken;
    }

    private long seedRoleId() {
        List<SysRoleDO> roles = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ROLE));
        assertFalse(roles.isEmpty(), "库中应存在角色种子");
        return roles.get(0).getId();
    }

    private long seedOrgId() {
        List<SysOrgDO> orgs = JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ORG));
        assertFalse(orgs.isEmpty(), "库中应存在部门种子");
        return orgs.get(0).getId();
    }

    private Long createUser(String username, String password, String mobile, String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("displayName", "IT用户-" + username);
        body.put("mobile", mobile);
        body.put("email", email);
        body.put("roleIds", List.of(seedRoleId()));
        body.put("deptIds", List.of(seedOrgId()));
        ResponseEntity<Result> resp = post("/api/system/users", body, Result.class, token());
        assertEquals(200, resp.getStatusCode().value(), "创建用户应成功: " + resp.getBody());
        Long id = Long.valueOf((String) resp.getBody().data());
        createdUserIds.add(id);
        return id;
    }

    // ==================== 创建边界 ====================

    /**
     * 异常：弱口令（满足长度但复杂度不足）→ 400。
     *
     * <p>PasswordPolicy 抛 {@code CommonErrorCode.VALIDATION_ERROR}（400），
     * 与 Bean Validation 的 400 口径一致；AUTH_006 PASSWORD_WEAK(422) 预留给
     * 登录链路的强度提示，当前设置密码的三处入口统一走 400。
     */
    @Test
    void createWithWeakPassword_returns400() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "it-ul-weak-" + System.nanoTime());
        body.put("password", "abcdefgh");
        body.put("displayName", "IT弱口令");
        body.put("roleIds", List.of(seedRoleId()));
        body.put("deptIds", List.of(seedOrgId()));
        ResponseEntity<Result> resp = post("/api/system/users", body, Result.class, token());
        assertEquals(400, resp.getStatusCode().value(), "弱口令应被拒绝");
        assertNotNull(resp.getBody());
        assertFalse(resp.getBody().isSuccess());
    }

    /** 异常：重复用户名 → 409。 */
    @Test
    void createWithDuplicateUsername_returns409() {
        String uname = "it-ul-dup-" + System.nanoTime();
        createUser(uname, "Password123!", null, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", uname);
        body.put("password", "Password123!");
        body.put("displayName", "IT重复用户名");
        body.put("roleIds", List.of(seedRoleId()));
        body.put("deptIds", List.of(seedOrgId()));
        ResponseEntity<Result> resp = post("/api/system/users", body, Result.class, token());
        assertEquals(409, resp.getStatusCode().value(), "重复用户名应返回 409");
    }

    /** 异常：手机号撞车 → 409（DB 唯一索引兜底）。 */
    @Test
    void createWithDuplicateMobile_returns409() {
        String mobile = "138" + String.format("%08d", System.nanoTime() % 100000000L);
        createUser("it-ul-m1-" + System.nanoTime(), "Password123!", mobile, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "it-ul-m2-" + System.nanoTime());
        body.put("password", "Password123!");
        body.put("displayName", "IT手机撞车");
        body.put("mobile", mobile);
        body.put("roleIds", List.of(seedRoleId()));
        body.put("deptIds", List.of(seedOrgId()));
        ResponseEntity<Result> resp = post("/api/system/users", body, Result.class, token());
        assertEquals(409, resp.getStatusCode().value(), "手机号撞车应返回 409");
    }

    /**
     * 异常（D-2 回归）：邮箱撞车 → 409。
     *
     * <p>邮箱无 DB 唯一索引，唯一性依赖服务层预校验（ProfileService 有、UserService 原来没有）。
     */
    @Test
    void createWithDuplicateEmail_returns409() {
        String email = "it-ul-" + System.nanoTime() + "@example.com";
        createUser("it-ul-e1-" + System.nanoTime(), "Password123!", null, email);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "it-ul-e2-" + System.nanoTime());
        body.put("password", "Password123!");
        body.put("displayName", "IT邮箱撞车");
        body.put("email", email);
        body.put("roleIds", List.of(seedRoleId()));
        body.put("deptIds", List.of(seedOrgId()));
        ResponseEntity<Result> resp = post("/api/system/users", body, Result.class, token());
        assertEquals(409, resp.getStatusCode().value(), "邮箱撞车应返回 409（D-2 修复回归）");
    }

    /** 异常：未绑定角色 → 400（@NotEmpty 守卫，至少绑定一个角色）。 */
    @Test
    void createWithoutRoles_returns400() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "it-ul-nr-" + System.nanoTime());
        body.put("password", "Password123!");
        body.put("displayName", "IT无角色");
        body.put("deptIds", List.of(seedOrgId()));
        ResponseEntity<Result> resp = post("/api/system/users", body, Result.class, token());
        assertEquals(400, resp.getStatusCode().value(), "未绑定角色应返回 400");
    }

    // ==================== 更新边界 ====================

    /**
     * 异常（D-1 回归）：非法状态值 → 400。
     *
     * <p>修复前 status 无白名单，任意字符串可落库（登录链路只识别 DISABLED/LOCKED），
     * 成为永久数据噪声；修复后仅接受 ACTIVE/DISABLED。
     */
    @Test
    void updateWithInvalidStatus_returns400() {
        Long uid = createUser("it-ul-st-" + System.nanoTime(), "Password123!", null, null);
        Map<String, Object> body = Map.of("status", "WEIRD_STATUS");
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/system/users/" + uid,
                HttpMethod.PUT, new HttpEntity<>(body, bearer(token())), Result.class);
        assertEquals(400, resp.getStatusCode().value(), "非法状态值应返回 400（D-1 修复回归）");
    }

    /** 异常：把手机号更新为他人已绑定的手机 → 409。 */
    @Test
    void updateMobileToOthersMobile_returns409() {
        String mobile = "139" + String.format("%08d", System.nanoTime() % 100000000L);
        Long holder = createUser("it-ul-h-" + System.nanoTime(), "Password123!", mobile, null);
        Long other = createUser("it-ul-o-" + System.nanoTime(), "Password123!", null, null);
        Map<String, Object> body = Map.of("mobile", mobile);
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/system/users/" + other,
                HttpMethod.PUT, new HttpEntity<>(body, bearer(token())), Result.class);
        assertEquals(409, resp.getStatusCode().value(), "手机号撞车应返回 409");
        assertNotNull(holder);
    }

    /** 正常：局部更新（仅传 displayName）不得清空未提交字段。 */
    @Test
    void updatePartial_keepsOtherFields() {
        String mobile = "137" + String.format("%08d", System.nanoTime() % 100000000L);
        Long uid = createUser("it-ul-p-" + System.nanoTime(), "Password123!", mobile, null);
        Map<String, Object> body = Map.of("displayName", "IT局部更新");
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/system/users/" + uid,
                HttpMethod.PUT, new HttpEntity<>(body, bearer(token())), Result.class);
        assertEquals(200, resp.getStatusCode().value());
        SysUserDO after = JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class, uid);
        assertNotNull(after);
        assertEquals("IT局部更新", after.getDisplayName());
        assertEquals(mobile, after.getMobile(), "未提交的 mobile 不应被清空");
    }

    // ==================== 删除与凭证 ====================

    /**
     * 异常：用户被删除后，其凭证不得再通过认证（登录 401）。
     *
     * <p>HTTP 客户端选型：预期响应为 401，TestRestTemplate 对带 body 请求恒处 streaming
     * 模式，收到 401 即抛 {@code HttpRetryException} 导致响应体不可读（同 AuthFlowIT 注释），
     * 故本用例的登录尝试走 JDK 内置 {@link java.net.http.HttpClient}。
     */
    @Test
    void deletedUserCannotLogin() throws Exception {
        String uname = "it-ul-del-" + System.nanoTime();
        Long uid = createUser(uname, "Password123!", null, null);
        // 清除「首登须改密」标记，隔离删除因素单独验证
        dsl.update(JooqTables.SYS_USER.table())
                .set(JooqTables.SYS_USER.field("password_expired", Integer.class), 0)
                .where(JooqTables.SYS_USER.field("id", Long.class).eq(uid))
                .execute();
        userService.delete(uid);
        createdUserIds.remove(uid);

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(baseUrl() + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", nextLoginIp())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + uname + "\",\"password\":\"Password123!\"}"))
                .build();
        java.net.http.HttpResponse<String> resp = java.net.http.HttpClient.newHttpClient()
                .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode(), "已删除用户登录应返回 401");
        Result<?> r = objectMapper.readValue(resp.body(), Result.class);
        assertNotNull(r);
        assertFalse(r.isSuccess());
        assertEquals("AUTH_001", r.code(), "已删除用户应按「用户名或密码错误」统一拒绝（防枚举）");
    }

    /** 正常：禁用用户可登录被拒（403），重新启用后恢复（状态机闭环）。 */
    @Test
    void disableThenEnableUser_loginRejectedThenRestored() {
        String uname = "it-ul-de-" + System.nanoTime();
        Long uid = createUser(uname, "Password123!", null, null);
        dsl.update(JooqTables.SYS_USER.table())
                .set(JooqTables.SYS_USER.field("password_expired", Integer.class), 0)
                .where(JooqTables.SYS_USER.field("id", Long.class).eq(uid))
                .execute();

        // 禁用
        ResponseEntity<Result> off = rest.exchange(baseUrl() + "/api/system/users/" + uid,
                HttpMethod.PUT, new HttpEntity<>(Map.of("status", "DISABLED"), bearer(token())), Result.class);
        assertEquals(200, off.getStatusCode().value());

        Map<String, String> loginBody = Map.of("username", uname, "password", "Password123!");
        org.springframework.http.HttpHeaders h1 = bearer(null);
        h1.set("X-Forwarded-For", nextLoginIp());
        ResponseEntity<Result> rejected = rest.postForEntity(baseUrl() + "/api/auth/login",
                new HttpEntity<>(loginBody, h1), Result.class);
        assertEquals(403, rejected.getStatusCode().value(), "禁用用户登录应返回 403");
        assertNotNull(rejected.getBody());
        assertEquals("AUTH_003", rejected.getBody().code());

        // 重新启用
        ResponseEntity<Result> on = rest.exchange(baseUrl() + "/api/system/users/" + uid,
                HttpMethod.PUT, new HttpEntity<>(Map.of("status", "ACTIVE"), bearer(token())), Result.class);
        assertEquals(200, on.getStatusCode().value());

        org.springframework.http.HttpHeaders h2 = bearer(null);
        h2.set("X-Forwarded-For", nextLoginIp());
        ResponseEntity<Result> ok = rest.postForEntity(baseUrl() + "/api/auth/login",
                new HttpEntity<>(loginBody, h2), Result.class);
        assertEquals(200, ok.getStatusCode().value(), "重新启用后应可登录");
        assertNotNull(ok.getBody());
        assertTrue(ok.getBody().isSuccess());
    }
}
