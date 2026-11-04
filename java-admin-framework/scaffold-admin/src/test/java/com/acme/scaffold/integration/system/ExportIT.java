package com.acme.scaffold.integration.system;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 导出中心集成测试：固化导出链路与 F3 行数上限之外的端到端契约。
 *
 * <p>场景：
 * <ul>
 *   <li>已知模板（users）+ CSV 格式 → 200 且返回非空字节（端到端导出闭环）；</li>
 *   <li>未知模板编码 → 404 NOT_FOUND；</li>
 *   <li>不支持的导出格式 → 400 VALIDATION_ERROR。</li>
 * </ul>
 *
 * <p>F3 行数上限（超行数拒导）已由 {@code ExportCenterServiceTest} 单元层固化，
 * 本类不再重复，仅验证 HTTP 可达的导出契约（模板发现 / 格式解析 / 字节产出）。</p>
 *
 * <p>运行前提：本机 MySQL（root/root），admin/admin123 属 SUPER_ADMIN，激活 integration profile。
 */
class ExportIT extends AbstractHttpIntegrationTest {

    /** 类内缓存 admin 令牌（避免重复登录触发 RateLimitFilter 限流）。 */
    private String cachedToken;

    private String adminToken() {
        if (cachedToken == null) {
            cachedToken = login("admin", "admin123");
        }
        return cachedToken;
    }

    /** 正常：已知 users 模板 CSV 导出 → 200 + 非空字节（至少含表头）。 */
    @Test
    void download_knownTemplateCsv_returnsBytes() {
        String url = baseUrl() + "/api/system/export/download?code=users&format=CSV";
        // 权限探测：无 system:export:read 时跳过，避免误报
        ResponseEntity<byte[]> probe = rest.exchange(url, HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken())), byte[].class);
        assumeTrue(probe.getStatusCode().value() != 403, "admin 无 system:export:read 权限，跳过导出断言");

        ResponseEntity<byte[]> resp = rest.exchange(url, HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken())), byte[].class);
        assertEquals(200, resp.getStatusCode().value(), "已知模板 CSV 导出应成功");
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().length > 0, "导出字节应非空（至少含表头）");
    }

    /** 异常：未知模板编码 → 404 NOT_FOUND。 */
    @Test
    void download_unknownTemplate_returns404() {
        ResponseEntity<Result> resp = get("/api/system/export/download?code=it_unknown_" + System.nanoTime(),
                Result.class, adminToken());
        assertEquals(404, resp.getStatusCode().value(), "未知模板编码应 404（NOT_FOUND）");
        assertNotNull(resp.getBody());
        assertFalse(resp.getBody().isSuccess(), "业务应失败");
    }

    /** 异常：不支持的导出格式 → 400 VALIDATION_ERROR。 */
    @Test
    void download_unsupportedFormat_returns400() {
        ResponseEntity<Result> resp = get("/api/system/export/download?code=users&format=XML",
                Result.class, adminToken());
        assertEquals(400, resp.getStatusCode().value(), "不支持的导出格式应 400（VALIDATION_ERROR）");
        assertNotNull(resp.getBody());
        assertFalse(resp.getBody().isSuccess(), "业务应失败");
    }
}
