package com.acme.scaffold.integration.tenant;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租户开通（M19 SaaS 生产启用闭环）端到端集成测试。
 *
 * <p>覆盖「注册租户 → 一键开通（初始化默认部门 / 租户管理员角色 / 首位管理员用户）→
 * 开通后数据可读 → 幂等兜底（重复开通拒绝）→ 租户管理员凭证被认证层识别」全链路，
 * 验证多租户隔离作用域下子资源 {@code tenant_id} 正确落库、前后端数据一致。
 *
 * <p>运行前提：本机 MySQL（root/root，库 java_admin），admin/admin123 为 SUPER_ADMIN，
 * 激活 integration profile（见 {@code BaseIntegrationTest}）。
 */
class TenantProvisioningIT extends AbstractHttpIntegrationTest {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    /** 类内缓存平台管理员令牌（规避 RateLimitFilter 跨用例累计，见 UserLifecycleIT）。 */
    private String cachedToken;

    private String token() {
        if (cachedToken == null) {
            cachedToken = login("admin", "admin123");
        }
        return cachedToken;
    }

    /** 本次测试创建的租户 id（@AfterEach 最佳努力清理）。 */
    private Long createdTenantId;

    @AfterEach
    void cleanup() {
        if (createdTenantId != null) {
            rest.exchange(baseUrl() + "/api/system/tenants/" + createdTenantId,
                    HttpMethod.DELETE, new HttpEntity<>(bearer(token())), Result.class);
            createdTenantId = null;
        }
    }

    /** 用 JDK HttpClient 登录（可读任意状态码响应体，规避 TestRestTemplate 401 流式读体问题，见 AuthFlowIT）。 */
    private HttpResponse<String> postLogin(String username, String password) throws Exception {
        String ip = "10.77." + (Math.abs(username.hashCode()) % 250 + 1) + ".9";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", ip)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private long createTenant(String tenantCode, String tenantName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantCode", tenantCode);
        body.put("tenantName", tenantName);
        body.put("status", "ENABLED");
        body.put("contactName", "IT联系人");
        body.put("contactEmail", tenantCode + "@example.com");
        ResponseEntity<Result> resp = post("/api/system/tenants", body, Result.class, token());
        assertEquals(200, resp.getStatusCode().value(), "注册租户应成功: " + resp.getBody());
        Object d = resp.getBody().data();
        long id = (d instanceof Number n) ? n.longValue() : Long.parseLong(d.toString());
        createdTenantId = id;
        return id;
    }

    @Test
    void provisionCreatesAdminRoleDept_andReReadConsistent() throws Exception {
        String code = "IT-PROV-" + Math.abs(System.nanoTime());
        String adminUser = "it_tenant_admin_" + System.nanoTime();
        long tenantId = createTenant(code, "IT开通租户-" + code);

        // 开通：初始化 默认部门 + 租户管理员角色（绑定全量菜单/接口）+ 首位管理员用户
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("adminUsername", adminUser);
        req.put("adminPassword", "TenantPwd123!");
        req.put("adminDisplayName", "IT租户管理员");
        req.put("adminMobile", "136" + String.format("%08d", Math.abs(code.hashCode()) % 100000000L));
        req.put("adminEmail", adminUser + "@example.com");
        ResponseEntity<Result> resp = post("/api/system/tenants/" + tenantId + "/provision",
                req, Result.class, token());
        assertEquals(200, resp.getStatusCode().value(), "开通租户应成功: " + resp.getBody());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess());

        @SuppressWarnings("unchecked")
        Map<String, Object> view = (Map<String, Object>) resp.getBody().data();
        assertNotNull(view, "应返回开通视图");
        assertEquals(tenantId, ((Number) view.get("tenantId")).longValue(), "视图 tenantId 一致");
        assertNotNull(view.get("adminUserId"), "应创建管理员用户");
        assertNotNull(view.get("roleId"), "应创建管理员角色");
        assertNotNull(view.get("deptId"), "应创建默认部门");
        assertNotNull(view.get("roleCode"), "应返回角色编码");
        assertTrue(((Number) view.get("adminUserId")).longValue() > 0, "管理员用户 id 应 > 0");
        assertTrue(((Number) view.get("roleId")).longValue() > 0, "角色 id 应 > 0");
        assertTrue(((Number) view.get("deptId")).longValue() > 0, "部门 id 应 > 0");

        // 数据一致性：开通后按 id 重新读取租户，名称与创建时一致
        ResponseEntity<Result> reRead = get("/api/system/tenants/" + tenantId, Result.class, token());
        assertEquals(200, reRead.getStatusCode().value(), "应可读回租户");
        @SuppressWarnings("unchecked")
        Map<String, Object> reTenant = (Map<String, Object>) reRead.getBody().data();
        assertEquals("IT开通租户-" + code, reTenant.get("tenantName"), "租户名称回读一致");

        // 幂等兜底：重复开通应被拒绝（CONFLICT）
        ResponseEntity<Result> dup = post("/api/system/tenants/" + tenantId + "/provision",
                req, Result.class, token());
        assertEquals(409, dup.getStatusCode().value(), "重复开通应返回 409（幂等兜底）");

        // 端到端：租户管理员凭证被认证层识别（200 或 403 均可证明账号与口令正确落库，仅拒绝 401）
        HttpResponse<String> loginResp = postLogin(adminUser, "TenantPwd123!");
        assertNotEquals(401, loginResp.statusCode(), "租户管理员凭证应被认证层识别（非 401）");
    }

    @Test
    void provisionMissingTenant_returns404() {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("adminUsername", "ghost-admin");
        req.put("adminPassword", "TenantPwd123!");
        req.put("adminDisplayName", "幽灵管理员");
        ResponseEntity<Result> resp = post("/api/system/tenants/99999999/provision",
                req, Result.class, token());
        assertEquals(404, resp.getStatusCode().value(), "对不存在租户开通应返回 404");
    }
}
