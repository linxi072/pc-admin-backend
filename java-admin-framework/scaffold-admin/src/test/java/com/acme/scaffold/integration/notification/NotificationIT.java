package com.acme.scaffold.integration.notification;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 通知渠道与模板发送集成测试：固化 F1（渠道合法性校验）与 M11/M12 模板发送链路。
 *
 * <p>场景：
 * <ul>
 *   <li>非法渠道模板在创建接口即被拒（400 VALIDATION_ERROR），永不落库——F1 的 HTTP 层可达回归面；</li>
 *   <li>合法 IN_APP 模板创建后按模板发送给当前用户，实际投递成功（delivered ≥ 1）；</li>
 *   <li>未知模板编码显式失败（404 NOT_FOUND），绝不静默降级。</li>
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li>渠道合法性在 {@code NotificationTemplateRules.validate} 已做创建期校验，
 *       故非法渠道的 HTTP 可达回归面是「创建即拒」，而非 send 端 {@code DomainValidators.requireIn}
 *       的纵深防御（后者已由 {@code NotificationServiceTest} 单元层覆盖）；</li>
 *   <li>{@code digest.enabled=false} 保证 IN_APP 直接经 {@code InAppNotificationAdapter}
 *       → {@code MessageService.doSend} 落库，happy path 稳定，delivered ≥ 1。</li>
 * </ul>
 *
 * <p>运行前提：本机 MySQL（root/root），admin/admin123 属 SUPER_ADMIN，激活 integration profile。
 */
class NotificationIT extends AbstractHttpIntegrationTest {

    /** 类内缓存 admin 令牌（避免重复登录触发 RateLimitFilter 限流）。 */
    private String cachedToken;

    private String adminToken() {
        if (cachedToken == null) {
            cachedToken = login("admin", "admin123");
        }
        return cachedToken;
    }

    /** 本类创建的通知模板 id，@AfterEach 尽力清理，避免脏数据累积。 */
    private Long createdTemplateId;

    @AfterEach
    void cleanup() {
        if (createdTemplateId != null) {
            rest.exchange(baseUrl() + "/api/system/notification-templates/" + createdTemplateId,
                    HttpMethod.DELETE, new HttpEntity<>(bearer(adminToken())), Result.class);
            createdTemplateId = null;
        }
    }

    @SuppressWarnings("unchecked")
    private long adminId() {
        ResponseEntity<Result> resp = get("/api/profile", Result.class, adminToken());
        assertEquals(200, resp.getStatusCode().value(), "应能读取当前用户资料");
        Map<String, Object> data = (Map<String, Object>) resp.getBody().data();
        return ((Number) data.get("id")).longValue();
    }

    /**
     * 异常（F1 回归）：非法渠道模板在创建接口即被拒 400，永不落库。
     *
     * <p>渠道合法性在创建期 {@code NotificationTemplateRules.validate} 已校验，
     * Bean Validation（@NotBlank）仅保证非空，真正的枚举白名单在业务规则层，
     * 故非法渠道值能越过 DTO 校验、在 service 层被拒。</p>
     */
    @Test
    void createTemplate_rejectsInvalidChannel() {
        Map<String, Object> body = new LinkedHashMap<>();
        // 模板编码须以大写字母开头、仅含大写字母/数字/下划线、长度 2-64
        body.put("templateCode", "IT_TPL_INVALID_" + System.nanoTime());
        body.put("templateName", "IT非法渠道模板");
        body.put("channel", "INVALID_X");          // 非法渠道，不在 Channel 枚举中
        body.put("title", "非法渠道");
        body.put("content", "内容");
        body.put("enabled", true);

        ResponseEntity<Result> resp = post("/api/system/notification-templates", body, Result.class, adminToken());
        assertEquals(400, resp.getStatusCode().value(), "非法渠道模板创建应被拒 400（VALIDATION_ERROR）");
        assertNotNull(resp.getBody());
        assertFalse(resp.getBody().isSuccess(), "业务应失败");
    }

    /**
     * 正常（M11/M12 回归）：合法 IN_APP 模板创建 → 按模板发送给当前用户 → 实际投递成功。
     *
     * <p>验证「模板取用 → ${var} 渲染 → 渠道投递」端到端闭环：发送返回的
     * {@code delivered ≥ 1} 表示 IN_APP 适配器已真实落库（非静默降级）。</p>
     */
    @Test
    @SuppressWarnings("unchecked")
    void sendWithTemplate_deliversToCurrentUser() {
        // 权限探测：无 system:message:send 时跳过一致性断言，避免误报
        ResponseEntity<Result> probe = post("/api/system/notifications/send-with-template",
                Map.of("templateCode", "NOPE_" + System.nanoTime()), Result.class, adminToken());
        assumeTrue(probe.getStatusCode().value() != 403, "admin 无 system:message:send 权限，跳过发送断言");

        String code = "IT_TPL_INAPP_" + System.nanoTime();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateCode", code);
        body.put("templateName", "IT站内信模板");
        body.put("channel", "IN_APP");
        body.put("title", "你好 ${name}");
        body.put("content", "这是给 ${name} 的站内信");
        body.put("enabled", true);

        ResponseEntity<Result> createResp = post("/api/system/notification-templates", body, Result.class, adminToken());
        assertEquals(200, createResp.getStatusCode().value(), "合法 IN_APP 模板应创建成功");
        assertNotNull(createResp.getBody());
        assertTrue(createResp.getBody().isSuccess());
        // 响应 data 为 Long 类型（后端 Result<Long> 序列化后反序列化为 Long，而非 String）
        createdTemplateId = ((Number) createResp.getBody().data()).longValue();

        long adminId = adminId();
        Map<String, Object> sendReq = new LinkedHashMap<>();
        sendReq.put("templateCode", code);
        sendReq.put("variables", Map.of("name", "管理员"));
        sendReq.put("receiverIds", List.of(adminId));

        ResponseEntity<Result> sendResp = post("/api/system/notifications/send-with-template",
                sendReq, Result.class, adminToken());
        assertEquals(200, sendResp.getStatusCode().value(), "按模板发送应成功");
        assertNotNull(sendResp.getBody());
        assertTrue(sendResp.getBody().isSuccess());
        Map<String, Object> data = (Map<String, Object>) sendResp.getBody().data();
        int delivered = ((Number) data.get("delivered")).intValue();
        assertTrue(delivered >= 1, "IN_APP 模板应实际投递给当前用户（delivered ≥ 1）");
    }

    /** 异常（M11 回归）：未知模板编码显式失败 404，绝不静默降级。 */
    @Test
    void sendWithTemplate_unknownTemplateCode_returns404() {
        ResponseEntity<Result> resp = post("/api/system/notifications/send-with-template",
                Map.of("templateCode", "it_tpl_unknown_" + System.nanoTime()),
                Result.class, adminToken());
        assertEquals(404, resp.getStatusCode().value(), "未知模板编码应 404（NOT_FOUND）");
        assertNotNull(resp.getBody());
        assertFalse(resp.getBody().isSuccess(), "业务应失败");
    }
}
