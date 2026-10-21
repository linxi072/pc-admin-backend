package com.acme.scaffold.integration.system;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.dto.CreateUserRequest;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.service.UserService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V29 系统管理新菜单集成测试：缓存监控 / 连接池监控 / 登录日志 / 操作日志。
 *
 * <p>场景：
 * <ul>
 *   <li>超管可读缓存统计（含 tokenVersionCache 条目与命中率字段）；</li>
 *   <li>缓存清理端点：存在 → 成功，不存在 → 业务失败（NOT_FOUND）；</li>
 *   <li>超管可读连接池统计（活跃/空闲/最大连接数等配置项）；</li>
 *   <li>超管可分页查询登录日志与统计摘要；</li>
 *   <li>超管可分页查询操作日志（AuditLogController 既有端点）；</li>
 *   <li>普通用户（无 system:login-log:read）访问登录日志 → 403（权限码隔离生效）。</li>
 * </ul>
 *
 * <p>运行前提：本机 MySQL（root/root），V29 迁移已随启动执行（菜单/接口资源/授权），
 * 激活 integration profile。
 */
class MonitorLogIT extends AbstractHttpIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private UserService userService;

    private final List<Long> createdUserIds = new ArrayList<>();

    /** 登录尝试序号：每次变换 IP 末段，规避 RateLimitFilter 跨运行累计。 */
    private final AtomicInteger loginSeq = new AtomicInteger();

    private String nextLoginIp() {
        String base = clientIp();
        int octet = loginSeq.incrementAndGet() % 250 + 1;
        return base.substring(0, base.lastIndexOf('.') + 1) + octet;
    }

    private String cachedAdminToken;

    private String adminToken() {
        if (cachedAdminToken == null) {
            cachedAdminToken = createSuperAdminUserAndLogin();
        }
        return cachedAdminToken;
    }

    @AfterEach
    void cleanup() {
        for (Long uid : createdUserIds) {
            userService.delete(uid);
        }
        createdUserIds.clear();
    }

    /** 创建持 SUPER_ADMIN 角色的测试用户并登录（resetPassword 顺带清 password_expired）。 */
    private String createSuperAdminUserAndLogin() {
        SysRoleDO superAdmin = JooqWriters.fetchOne(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                JooqTables.SYS_ROLE.field("role_code", String.class).eq("SUPER_ADMIN"));
        assertNotNull(superAdmin, "库中应存在 SUPER_ADMIN 角色");
        List<SysOrgDO> orgs = JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ORG));
        assertFalse(orgs.isEmpty(), "库中应存在部门种子");
        String uname = "it-mon-" + System.nanoTime();
        Long uid = userService.create(new CreateUserRequest(
                uname, "Initial123!", "IT监控用户", null, null,
                List.of(superAdmin.getId()), null, List.of(orgs.get(0).getId()), null, null));
        createdUserIds.add(uid);
        userService.resetPassword(uid, "Reset123!");
        return loginAs(uname, "Reset123!");
    }

    private String loginAs(String username, String password) {
        Map<String, String> body = Map.of("username", username, "password", password);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Forwarded-For", nextLoginIp());
        ResponseEntity<Result> resp = rest.postForEntity(baseUrl() + "/api/auth/login",
                new HttpEntity<>(body, h), Result.class);
        assertEquals(200, resp.getStatusCode().value(), "登录应成功");
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess(), "登录应成功: " + resp.getBody().message());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getBody().data();
        return (String) data.get("accessToken");
    }

    // ---------------- 缓存监控 ----------------

    /** 缓存统计：200 且含 tokenVersionCache 条目（name/type/entries/hitRate 字段齐全）。 */
    @Test
    void cacheStats_containsTokenVersionCache() {
        ResponseEntity<Result> resp = get("/api/system/monitor/caches", Result.class, adminToken());
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess(), "查询缓存统计应成功");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> caches = (List<Map<String, Object>>) resp.getBody().data();
        assertNotNull(caches, "缓存列表不应为 null");
        assertFalse(caches.isEmpty(), "至少应有 tokenVersionCache 一项");
        Map<String, Object> tv = caches.stream()
                .filter(c -> "token-version".equals(c.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应包含 token-version: " + caches));
        assertNotNull(tv.get("type"), "实现类型不应为 null");
        assertNotNull(tv.get("entries"), "条目数不应为 null");
        assertTrue(tv.containsKey("hitRate"), "应包含命中率字段（无访问时可为 null）");
        assertTrue(tv.containsKey("ttlMillis"), "应包含 TTL 字段");
    }

    /** 缓存清理：存在的缓存清理成功，不存在的名称返回业务失败而非 500。 */
    @Test
    void clearCache_existingAndUnknown() {
        String token = adminToken();
        ResponseEntity<Result> ok = post("/api/system/monitor/caches/token-version/clear",
                null, Result.class, token);
        assertEquals(200, ok.getStatusCode().value());
        assertNotNull(ok.getBody());
        assertTrue(ok.getBody().isSuccess(), "清理已存在缓存应成功");

        ResponseEntity<Result> missing = post("/api/system/monitor/caches/no-such-cache/clear",
                null, Result.class, token);
        assertEquals(200, missing.getStatusCode().value());
        assertNotNull(missing.getBody());
        assertFalse(missing.getBody().isSuccess(), "清理不存在的缓存应业务失败");
    }

    // ---------------- 连接池监控 ----------------

    /** 连接池统计：200 且含活跃/空闲/最大连接数（HikariCP 运行时数据可达）。 */
    @Test
    void datasourceStats_containsPoolMetrics() {
        ResponseEntity<Result> resp = get("/api/system/monitor/datasource", Result.class, adminToken());
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess(), "查询连接池统计应成功");
        @SuppressWarnings("unchecked")
        Map<String, Object> pool = (Map<String, Object>) resp.getBody().data();
        assertNotNull(pool, "连接池数据不应为 null");
        assertTrue(pool.containsKey("activeCount"), "应包含活跃连接数");
        assertTrue(pool.containsKey("idleCount"), "应包含空闲连接数");
        assertTrue(pool.containsKey("maximumPoolSize"), "应包含最大连接数配置");
        assertNotNull(pool.get("jdbcUrl"), "应包含 JDBC URL");
    }

    // ---------------- 登录日志 ----------------

    /** 登录日志分页：200 且返回分页结构（list/total）。 */
    @Test
    void loginLogPage_returnsPageResult() {
        ResponseEntity<Result> resp = get("/api/system/login-logs/page?page=1&size=5",
                Result.class, adminToken());
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess(), "查询登录日志应成功");
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) resp.getBody().data();
        assertNotNull(page.get("records"), "分页结果应含 records");
        assertNotNull(page.get("total"), "分页结果应含 total");
    }

    /** 登录日志摘要：200 且含总数/成功/失败统计键。 */
    @Test
    void loginLogSummary_returnsAggregates() {
        ResponseEntity<Result> resp = get("/api/system/login-logs/summary",
                Result.class, adminToken());
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess(), "查询登录日志摘要应成功");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) resp.getBody().data();
        assertNotNull(summary.get("total"), "摘要应含总次数");
        assertNotNull(summary.get("success"), "摘要应含成功次数");
        assertNotNull(summary.get("failure"), "摘要应含失败次数");
    }

    // ---------------- 操作日志 ----------------

    /** 操作日志分页：200 且返回分页结构（AuditLogController 既有端点回归）。 */
    @Test
    void auditLogPage_returnsPageResult() {
        ResponseEntity<Result> resp = get("/api/system/audit-logs/page?page=1&size=5",
                Result.class, adminToken());
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess(), "查询操作日志应成功");
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) resp.getBody().data();
        assertNotNull(page.get("records"), "分页结果应含 records");
        assertNotNull(page.get("total"), "分页结果应含 total");
    }

    // ---------------- 权限控制 ----------------

    /** 普通用户（无 system:login-log:read）访问登录日志 → 403（敏感日志权限隔离）。 */
    @Test
    void loginLog_withoutPermission_returns403() {
        List<SysRoleDO> plainRoles = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                JooqTables.SYS_ROLE.field("role_code", String.class).ne("SUPER_ADMIN"));
        assertFalse(plainRoles.isEmpty(), "库中应存在非超管角色种子");
        SysRoleDO plainRole = plainRoles.get(0);
        List<SysOrgDO> orgs = JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ORG));
        String uname = "it-mon-plain-" + System.nanoTime();
        Long uid = userService.create(new CreateUserRequest(
                uname, "Initial123!", "IT监控普通用户", null, null,
                List.of(plainRole.getId()), null, List.of(orgs.get(0).getId()), null, null));
        createdUserIds.add(uid);
        userService.resetPassword(uid, "Reset123!");
        String token = loginAs(uname, "Reset123!");

        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/system/login-logs/page?page=1&size=5",
                HttpMethod.GET, new HttpEntity<>(bearer(token)), Result.class);
        assertEquals(403, resp.getStatusCode().value(),
                "无 system:login-log:read 权限应被拒绝: " + resp.getBody());
    }
}
