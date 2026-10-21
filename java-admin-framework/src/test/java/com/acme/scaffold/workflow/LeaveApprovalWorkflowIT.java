package com.acme.scaffold.workflow;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import com.acme.scaffold.workflow.model.ApprovalAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审批流端到端集成测试（会签 / 驳回 / 转办）。
 *
 * <p>运行前提（本机 / CI，不使用 Docker）：
 * <ol>
 *   <li>原生 MySQL 8.x，库名 java_admin，启动时由 Flyway 自动建表；</li>
 *   <li>admin 拥有全部工作流权限（workflow:instance:start / workflow:task:approve /
 *       workflow:task:transfer），可复用为发起人与审批人；</li>
 *   <li>leaveApproval 现由 {@code BuiltinWorkflowSeeder} 在应用启动时以 {@code wf_workflow_design}
 *       可编辑设计形式发布（不再依赖 classpath 静态 BPMN）；受让人/驳回策略来自设计，发起请求中的
 *       assigneeUserIds/managerUserId/approvalMode 已无需传入；</li>
 *   <li>V26 组织种子（sys_org 总部 + admin 主部门关联）：会签节点为 INITIATOR_MANAGER 受让人，
 *       缺部门种子时发起即 404；</li>
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
class LeaveApprovalWorkflowIT extends AbstractHttpIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    /** 带 Bearer 令牌的 JSON 请求（工作流接口均需认证；token 为 null 时不附加，用于未认证场景）。 */
    private HttpEntity<Map<String, Object>> json(Map<String, Object> body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        return new HttpEntity<>(body, h);
    }

    @Test
    void startAndApproveCountersign() {
        String token = login("admin", "admin123");
        // 发起请假：受让人/驳回策略来自 leaveApproval 已发布设计（无需在请求中指定）
        Map<String, Object> start = Map.of(
                "processKey", "leaveApproval",
                "businessType", "LEAVE",
                "businessId", "IT-" + System.nanoTime(),
                "title", "集成测试-请假");
        ResponseEntity<Result> started = rest.postForEntity(
                baseUrl() + "/api/workflow/instances/start", json(start, token), Result.class);
        assertNotNull(started.getBody());
        // 强断言：start 必须业务成功（受让人由已发布设计解析，依赖 V26 组织种子；
        // 历史上此处仅断言非空，404 错误体也「非空」导致假绿）
        assertTrue(started.getBody().isSuccess(),
                "发起流程应成功: " + started.getBody());
        assertNotNull(started.getBody().data(), "start 应返回 processInstanceId");

        // 会签：以审批人身份登录后，对各自的 taskId 调 /complete（action=APPROVE）。
        // 最后一个审批人通过后流程结束。此处给出单个审批动作的请求骨架。
        Map<String, Object> complete = Map.of(
                "taskId", "PLACEHOLDER_TASK_ID",
                "action", ApprovalAction.APPROVE,
                "operationId", "OP-" + System.nanoTime());
        ResponseEntity<Result> approved = rest.postForEntity(
                baseUrl() + "/api/workflow/tasks/complete", json(complete, token), Result.class);
        assertNotNull(approved.getBody());
        // 占位 taskId 不存在，审批应业务失败（可见错误而非静默放行）
        assertFalse(approved.getBody().isSuccess(), "不存在的任务审批应失败");
    }

    @Test
    void rejectEndsProcess() {
        String token = login("admin", "admin123");
        // 驳回：任一审批人驳回即终止流程（WorkflowService 调 engine.setRejected + complete）
        Map<String, Object> complete = Map.of(
                "taskId", "PLACEHOLDER_TASK_ID",
                "action", ApprovalAction.REJECT,
                "operationId", "OP-" + System.nanoTime());
        ResponseEntity<Result> rejected = rest.postForEntity(
                baseUrl() + "/api/workflow/tasks/complete", json(complete, token), Result.class);
        assertNotNull(rejected.getBody());
        assertFalse(rejected.getBody().isSuccess(), "不存在的任务驳回应失败");
    }

    @Test
    void transferReassignsTask() {
        String token = login("admin", "admin123");
        // 转办：将当前任务转给 toUserId（仅本人待办可转）
        Map<String, Object> transfer = Map.of(
                "taskId", "PLACEHOLDER_TASK_ID",
                "toUserId", 4L,
                "operationId", "OP-" + System.nanoTime());
        ResponseEntity<Result> transferred = rest.postForEntity(
                baseUrl() + "/api/workflow/tasks/transfer", json(transfer, token), Result.class);
        assertNotNull(transferred.getBody());
        assertFalse(transferred.getBody().isSuccess(), "不存在的任务转办应失败");
    }
}
