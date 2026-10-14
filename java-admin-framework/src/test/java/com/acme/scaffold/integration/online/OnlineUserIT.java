package com.acme.scaffold.integration.online;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.CreateUserRequest;
import com.acme.scaffold.system.entity.SysOnlineSessionDO;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.service.OnlineUserService;
import com.acme.scaffold.system.service.UserService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在线用户管理集成测试：验证「登录登记 → 列表/筛选 → 强制下线 → 限制登录 → 解除」全链路。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>登录后在线列表出现当前会话（账号/IP/状态）；</li>
 *   <li>账号模糊、IP 模糊筛选；</li>
 *   <li>强制下线：会话置 OFFLINE + 原因 FORCE_LOGOUT，重复下线幂等 404；</li>
 *   <li>限制登录：被限用户登录被拒（423 AUTH_015）、在线会话连带下线、
 *       限制记录可查、解除后登录恢复；</li>
 *   <li>时长白名单（400）、重复限制（409）、用户不存在（404）守卫。</li>
 * </ul>
 *
 * <p>运行前提（本机 / CI，不使用 Docker）：原生 MySQL 8.x，库名 java_admin，
 * Flyway 自动执行 V27 迁移；admin/admin123 属于 SUPER_ADMIN。
 */
class OnlineUserIT extends AbstractHttpIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private UserService userService;

    /** 本类创建的测试用户，@AfterEach 经删除接口清理，避免脏数据累积。 */
    private final List<Long> createdUserIds = new ArrayList<>();

    /**
     * 登录尝试序号：每次尝试变换 IP 末段。
     *
     * <p>背景：{@code RateLimitFilter} 按「login:IP」60s 窗口限流 10 次且过滤器为单例，
     * 同一测试类重复运行（如修复后重跑）时计数跨运行累计，会误触 429。
     * 与 {@code clientIp()} 的类级隔离同思路，这里细化到调用级。
     */
    private final java.util.concurrent.atomic.AtomicInteger loginSeq =
            new java.util.concurrent.atomic.AtomicInteger();

    private String nextLoginIp() {
        String base = clientIp();
        int octet = loginSeq.incrementAndGet() % 250 + 1;
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

    /** 裸登录尝试（不抛异常），用于断言被限用户登录被拒的场景。 */
    private ResponseEntity<Result> attemptLogin(String username, String password) {
        Map<String, String> body = Map.of("username", username, "password", password);
        org.springframework.http.HttpHeaders h = bearer(null);
        h.set("X-Forwarded-For", nextLoginIp());
        return rest.postForEntity(baseUrl() + "/api/auth/login", new HttpEntity<>(body, h), Result.class);
    }

    /** 创建测试用户（唯一用户名），返回 userId；密码固定为满足强度要求的夹具值。 */
    private Long createTestUser(String tag) {
        List<SysRoleDO> roles = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ROLE));
        assertFalse(roles.isEmpty(), "库中应存在角色种子");
        List<SysOrgDO> orgs = JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ORG));
        assertFalse(orgs.isEmpty(), "库中应存在部门种子");
        String uniq = "it-online-" + tag + "-" + System.nanoTime();
        Long uid = userService.create(new CreateUserRequest(
                uniq, "Password123!", "在线测试 " + tag, null, null,
                List.of(roles.get(0).getId()), null, List.of(orgs.get(0).getId()), null));
        createdUserIds.add(uid);
        // UserService.create 默认置 password_expired=1（首登强制改密），会先于限制校验返回
        // 423 MUST_CHANGE_PASSWORD，干扰本类「限制登录」断言；直接清除该标记。
        dsl.update(JooqTables.SYS_USER.table())
                .set(JooqTables.SYS_USER.field("password_expired", Integer.class), 0)
                .where(JooqTables.SYS_USER.field("id", Long.class).eq(uid))
                .execute();
        return uid;
    }

    private String usernameOf(Long uid) {
        SysUserDO user = JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class, uid);
        assertNotNull(user, "测试用户应存在");
        return user.getUsername();
    }

    /** 调用在线用户分页接口，返回 records 列表（元素为 LinkedHashMap）。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> onlineRecords(String username, String ip, String status) {
        StringBuilder qs = new StringBuilder("/api/system/online-users/page?size=100");
        if (username != null) {
            qs.append("&username=").append(username);
        }
        if (ip != null) {
            qs.append("&ip=").append(ip);
        }
        if (status != null) {
            qs.append("&status=").append(status);
        }
        ResponseEntity<Result> resp = get(qs.toString(), Result.class, token());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess(), "在线用户分页应成功");
        Map<String, Object> page = (Map<String, Object>) resp.getBody().data();
        return (List<Map<String, Object>>) page.get("records");
    }

    /** 在分页结果中按用户名找会话（雪花 ID 为字符串序列化，需解析）。 */
    private Map<String, Object> findSessionByUsername(List<Map<String, Object>> records, String username) {
        return records.stream()
                .filter(r -> username.equals(r.get("username")))
                .findFirst()
                .orElse(null);
    }

    /** 直接查库取会话行（绕过列表接口，验证持久化真相）。 */
    private SysOnlineSessionDO sessionRow(Long userId) {
        return JooqWriters.fetchOne(dsl, JooqTables.SYS_ONLINE_SESSION, SysOnlineSessionDO.class,
                JooqTables.SYS_ONLINE_SESSION.field("user_id", Long.class).eq(userId));
    }

    // ------------------------------------------------------------------

    /** 登录登记闭环：登录后当前账号应出现在线列表，状态 ONLINE，IP 为登录请求 IP。 */
    @Test
    void pageShowsCurrentAdminSessionAfterLogin() {
        List<Map<String, Object>> records = onlineRecords("admin", null, null);
        Map<String, Object> admin = findSessionByUsername(records, "admin");
        assertNotNull(admin, "登录后 admin 应出现在线列表");
        assertEquals("ONLINE", admin.get("status"));
        assertEquals(clientIp(), admin.get("loginIp"), "登录 IP 应取自 X-Forwarded-For");
        assertNotNull(admin.get("loginAt"));
        assertNotNull(admin.get("lastActiveAt"));
    }

    /** 筛选口径：账号模糊匹配、IP 模糊匹配。 */
    @Test
    void pageFiltersByUsernameAndIp() {
        List<Map<String, Object>> byName = onlineRecords("admi", null, null);
        assertFalse(byName.isEmpty(), "模糊账号应至少命中 admin 自身会话");
        for (Map<String, Object> r : byName) {
            assertTrue(String.valueOf(r.get("username")).contains("admi"),
                    "账号筛选应模糊匹配: " + r.get("username"));
        }
        List<Map<String, Object>> byIp = onlineRecords(null, clientIp(), null);
        assertFalse(byIp.isEmpty(), "本机 IP 应至少命中 admin 自身会话");
        for (Map<String, Object> r : byIp) {
            assertTrue(String.valueOf(r.get("loginIp")).contains(clientIp()),
                    "IP 筛选应模糊匹配: " + r.get("loginIp"));
        }
    }

    /**
     * 强制下线闭环：会话置 OFFLINE（原因 FORCE_LOGOUT），重复下线同一会话幂等 404。
     * 被下线的是测试用户会话，不影响 admin 令牌继续调用。
     */
    @Test
    void forceLogoutTakesSessionOfflineAndSecondCallIsIdempotent() {
        Long uid = createTestUser("fl");
        String uniq = usernameOf(uid);
        assertEquals(200, attemptLogin(uniq, "Password123!").getStatusCode().value(), "测试用户登录应成功");

        List<Map<String, Object>> records = onlineRecords(uniq, null, null);
        Map<String, Object> session = findSessionByUsername(records, uniq);
        assertNotNull(session, "测试用户登录后应登记在线会话");
        Long sessionId = Long.parseLong(session.get("id").toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionIds", List.of(sessionId));
        ResponseEntity<Result> resp = post("/api/system/online-users/force-logout", body, Result.class, token());
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess(), "强制下线应成功");
        assertEquals(1, ((Number) resp.getBody().data()).intValue(), "应下线 1 个会话");

        // 持久化真相：行状态 OFFLINE + 原因 FORCE_LOGOUT
        SysOnlineSessionDO row = sessionRow(uid);
        assertNotNull(row, "会话行应存在（不物理删除）");
        assertEquals(SysOnlineSessionDO.STATUS_OFFLINE, row.getStatus());
        assertEquals(OnlineUserService.REASON_FORCE_LOGOUT, row.getOfflineReason());

        // 幂等：再次下线同一会话 → 404（不在线即无目标）
        ResponseEntity<Result> again = post("/api/system/online-users/force-logout", body, Result.class, token());
        assertEquals(404, again.getStatusCode().value(), "已下线会话再次下线应 404");
    }

    /**
     * 限制登录闭环：限制后登录被拒（423 AUTH_015）且在线会话连带下线；
     * 限制记录可查；手动解除后登录恢复。
     */
    @Test
    void restrictBlocksLoginTakesSessionsOfflineAndReleaseRestores() {
        Long uid = createTestUser("rs");
        String uniq = usernameOf(uid);
        assertEquals(200, attemptLogin(uniq, "Password123!").getStatusCode().value(), "前置：登录成功");
        assertNotNull(findSessionByUsername(onlineRecords(uniq, null, null), uniq),
                "前置：应登记在线会话");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("durationMinutes", 30);
        body.put("reason", "IT 集成测试临时限制");
        ResponseEntity<Result> resp = post("/api/system/online-users/users/" + uid + "/restrict",
                body, Result.class, token());
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess(), "设置限制应成功");

        // 登录被拒：423 + 稳定业务码 AUTH_015
        ResponseEntity<Result> blocked = attemptLogin(uniq, "Password123!");
        assertEquals(423, blocked.getStatusCode().value(), "被限用户登录应 423");
        assertNotNull(blocked.getBody());
        assertEquals("AUTH_015", blocked.getBody().code());

        // 连带下线：在线列表不再出现该用户
        assertNull(findSessionByUsername(onlineRecords(uniq, null, null), uniq),
                "限制后在线会话应被连带下线");

        // 限制记录可查：状态 ACTIVE、时长 30、原因一致
        ResponseEntity<Result> rp = get("/api/system/online-users/restrictions/page?username=" + uniq,
                Result.class, token());
        assertNotNull(rp.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> rpage = (Map<String, Object>) rp.getBody().data();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rrecords = (List<Map<String, Object>>) rpage.get("records");
        assertFalse(rrecords.isEmpty(), "限制记录应可查");
        Map<String, Object> record = rrecords.get(0);
        assertEquals("ACTIVE", record.get("status"));
        assertEquals(30, ((Number) record.get("durationMinutes")).intValue());
        assertEquals("IT 集成测试临时限制", record.get("reason"));

        // 手动解除 → 登录恢复
        ResponseEntity<Result> rel = post("/api/system/online-users/users/" + uid + "/release",
                null, Result.class, token());
        assertEquals(200, rel.getStatusCode().value());
        assertNotNull(rel.getBody());
        assertTrue(((Number) rel.getBody().data()).intValue() >= 1, "应解除至少 1 条限制");
        assertEquals(200, attemptLogin(uniq, "Password123!").getStatusCode().value(),
                "解除限制后登录应恢复");
    }

    /** 守卫：时长不在白名单 → 400。 */
    @Test
    void restrictRejectsDurationOutsideWhitelist() {
        Long uid = createTestUser("bad");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("durationMinutes", 1);
        body.put("reason", "非法时长");
        ResponseEntity<Result> resp = post("/api/system/online-users/users/" + uid + "/restrict",
                body, Result.class, token());
        assertEquals(400, resp.getStatusCode().value(), "非白名单时长应 400");
    }

    /** 守卫：重复限制 → 409。 */
    @Test
    void restrictRejectsDuplicate() {
        Long uid = createTestUser("dup");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("durationMinutes", 60);
        body.put("reason", "首次限制");
        ResponseEntity<Result> first = post("/api/system/online-users/users/" + uid + "/restrict",
                body, Result.class, token());
        assertEquals(200, first.getStatusCode().value(), "首次限制应成功");
        ResponseEntity<Result> second = post("/api/system/online-users/users/" + uid + "/restrict",
                body, Result.class, token());
        assertEquals(409, second.getStatusCode().value(), "重复限制应 409");
    }

    /** 守卫：用户不存在 → 404。 */
    @Test
    void restrictUnknownUserReturns404() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("durationMinutes", 60);
        body.put("reason", "不存在的用户");
        ResponseEntity<Result> resp = post("/api/system/online-users/users/999999999999/restrict",
                body, Result.class, token());
        assertEquals(404, resp.getStatusCode().value(), "用户不存在应 404");
    }
}
