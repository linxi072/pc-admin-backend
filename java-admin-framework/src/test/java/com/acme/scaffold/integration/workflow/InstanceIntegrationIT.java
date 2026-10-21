package com.acme.scaffold.integration.workflow;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工作流实例详情集成测试：验证 {@code GET /api/workflow/instances/{processInstanceId}}
 * 返回实例基础信息 + 当前活跃任务 + 历史审批记录，并对不存在的实例返回 404。
 *
 * <p>运行前提：本机 MySQL（root/root，scaffold 库已通过 Flyway 注入 V1–V18 种子，含 admin 账号
 * 与内置审批流 leaveApproval），激活 {@code integration} profile。
 *
 * <p>运行：{@code mvn -o test -Dtest=InstanceIntegrationIT -Dspring.profiles.active=integration}
 */
class InstanceIntegrationIT extends AbstractHttpIntegrationTest {

    @Test
    void detail_returnsInstanceWithTasksAndRecords() {
        String token = login("admin", "admin123");

        // 发起一个内置审批流实例，拿到 processInstanceId（start 直接返回该字符串）
        Map<String, Object> startBody = Map.of(
                "processKey", "leaveApproval",
                "businessType", "LEAVE",
                "businessId", "G6-" + System.nanoTime(),
                "title", "G6 集成测试流程",
                "formFields", Map.of("reason", "集成测试"));
        ResponseEntity<Result> startResp = post("/api/workflow/instances/start", startBody, Result.class, token);
        assertEquals(200, startResp.getStatusCode().value());
        assertNotNull(startResp.getBody());
        assertTrue(startResp.getBody().isSuccess());

        String processInstanceId = (String) startResp.getBody().data();
        assertNotNull(processInstanceId);

        // 查询实例详情
        ResponseEntity<Result> detailResp = get("/api/workflow/instances/" + processInstanceId, Result.class, token);
        assertEquals(200, detailResp.getStatusCode().value());
        assertNotNull(detailResp.getBody());
        assertTrue(detailResp.getBody().isSuccess());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) detailResp.getBody().data();
        assertEquals(processInstanceId, data.get("processInstanceId"));
        assertNotNull(data.get("status"));
        assertNotNull(data.get("starterUsername"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) data.get("currentTasks");
        assertNotNull(tasks);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        assertNotNull(records);
    }

    @Test
    void detail_notFound_returns404() {
        String token = login("admin", "admin123");
        ResponseEntity<Result> resp = get("/api/workflow/instances/NO_SUCH_INSTANCE_ID", Result.class, token);
        assertEquals(404, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals(false, resp.getBody().isSuccess());
    }
}
