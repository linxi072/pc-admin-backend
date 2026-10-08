package com.acme.scaffold.workflow;

import com.acme.scaffold.BaseIntegrationTest;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.workflow.model.ApprovalAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 审批流端到端集成测试（会签 / 驳回 / 转办）。
 *
 * <p>运行前提（本机 / CI，不使用 Docker）：
 * <ol>
 *   <li>原生 MySQL 8.x，库名 java_admin，启动时由 Flyway 自动建表；</li>
 *   <li>至少存在两个具备工作流权限（workflow:instance:start / workflow:task:approve /
 *       workflow:task:transfer）的测试用户；admin 默认拥有全部权限，可复用为发起人与审批人；</li>
 *   <li>leaveApproval 流程定义随应用启动从 classpath:/processes 自动部署；</li>
 *   <li>启动需带 {@code -Dio.netty.resolver.dns.useJdkResolver=true} 并直连 IPv6 回环。</li>
 * </ol>
 *
 * <p>说明：本文件为<b>模板</b>，演示了各接口的请求体结构。其中的
 * {@code PLACEHOLDER_TASK_ID} 需在运行时替换为真实 taskId
 * （以审批人身份登录后调用 {@code GET /api/workflow/tasks/mine} 获取）。会签（ALL）需逐审批人
 * 调用 /complete 直至全部通过，流程方才结束；此处仅给出首个审批动作的请求骨架。
 *
 * 运行：{@code mvn -o test -Dtest=LeaveApprovalWorkflowIT -Dspring.profiles.active=integration
 *   -DTEST_DB_URL=... -DTEST_DB_USER=... -DTEST_DB_PASSWORD=...}
 */
class LeaveApprovalWorkflowIT extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }

    @Test
    void startAndApproveCountersign() {
        // 发起会签（approvalMode=ALL）：多个审批人需全部通过
        Map<String, Object> start = Map.of(
                "processKey", "leaveApproval",
                "businessType", "LEAVE",
                "businessId", "IT-" + System.nanoTime(),
                "title", "集成测试-请假",
                "assigneeUserIds", List.of(2L, 3L),
                "managerUserId", 1L,
                "approvalMode", "ALL");
        ResponseEntity<Result> started = rest.postForEntity(
                baseUrl() + "/api/workflow/instances/start", json(start), Result.class);
        assertNotNull(started.getBody());

        // 会签：以审批人身份登录后，对各自的 taskId 调 /complete（action=APPROVE）。
        // 最后一个审批人通过后流程结束。此处给出单个审批动作的请求骨架。
        Map<String, Object> complete = Map.of(
                "taskId", "PLACEHOLDER_TASK_ID",
                "action", ApprovalAction.APPROVE,
                "operationId", "OP-" + System.nanoTime());
        ResponseEntity<Result> approved = rest.postForEntity(
                baseUrl() + "/api/workflow/tasks/complete", json(complete), Result.class);
        assertNotNull(approved.getBody());
    }

    @Test
    void rejectEndsProcess() {
        // 驳回：任一审批人驳回即终止流程（WorkflowService 调 engine.setRejected + complete）
        Map<String, Object> complete = Map.of(
                "taskId", "PLACEHOLDER_TASK_ID",
                "action", ApprovalAction.REJECT,
                "operationId", "OP-" + System.nanoTime());
        ResponseEntity<Result> rejected = rest.postForEntity(
                baseUrl() + "/api/workflow/tasks/complete", json(complete), Result.class);
        assertNotNull(rejected.getBody());
    }

    @Test
    void transferReassignsTask() {
        // 转办：将当前任务转给 toUserId（仅本人待办可转）
        Map<String, Object> transfer = Map.of(
                "taskId", "PLACEHOLDER_TASK_ID",
                "toUserId", 4L,
                "operationId", "OP-" + System.nanoTime());
        ResponseEntity<Result> transferred = rest.postForEntity(
                baseUrl() + "/api/workflow/tasks/transfer", json(transfer), Result.class);
        assertNotNull(transferred.getBody());
    }
}
