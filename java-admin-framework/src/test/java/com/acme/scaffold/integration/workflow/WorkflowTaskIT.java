package com.acme.scaffold.integration.workflow;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工作流任务操作集成测试：认领 / 转办 / 审批的守卫与闭环，以及超级管理员对
 * 工作流定义与任务接口的访问权（D-5 回归）。
 *
 * <p>场景：
 * <ul>
 *   <li>超管可访问工作流定义列表、任务认领端点可达（D-5：V11 后新增权限码补授）；</li>
 *   <li>审批缺少 Idempotency-Key → 400（强制幂等键）；</li>
 *   <li>非任务处理人审批 → 403（服务级守卫，先于引擎操作）；</li>
 *   <li>转办后新处理人可审批，审批记录含 TRANSFER + APPROVE 两条；</li>
 *   <li>同一 operationId 重复审批幂等（不产生第二条审批记录）；</li>
 *   <li>已是自己的任务重复认领 → 幂等成功。</li>
 * </ul>
 *
 * <p>运行前提：本机 MySQL（root/root），leaveApproval 已发布设计（首节点
 * INITIATOR_MANAGER，管理员本人持 MANAGER 角色，发起后任务分派给自己），
 * 激活 integration profile。
 */
class WorkflowTaskIT extends AbstractHttpIntegrationTest {

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
            cachedAdminToken = login("admin", "admin123");
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

    /** 创建持 SUPER_ADMIN 角色的测试用户（具备 workflow:task:approve 等全部权限码）。 */
    private String createSuperAdminUser(String tag) {
        SysRoleDO superAdmin = JooqWriters.fetchOne(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                JooqTables.SYS_ROLE.field("role_code", String.class).eq("SUPER_ADMIN"));
        assertNotNull(superAdmin, "库中应存在 SUPER_ADMIN 角色");
        List<SysOrgDO> orgs = JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ORG));
        assertFalse(orgs.isEmpty(), "库中应存在部门种子");
        String uname = "it-wf-" + tag + "-" + System.nanoTime();
        Long uid = userService.create(new CreateUserRequest(
                uname, "Initial123!", "IT工作流-" + tag, null, null,
                List.of(superAdmin.getId()), null, List.of(orgs.get(0).getId()), null));
        createdUserIds.add(uid);
        userService.resetPassword(uid, "Reset123!");
        return uname;
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
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getBody().data();
        return (String) data.get("accessToken");
    }

    /** 发起 leaveApproval 流程，返回 processInstanceId。 */
    private String startProcess(String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("processKey", "leaveApproval");
        body.put("businessType", "LEAVE");
        body.put("businessId", "IT-WF-" + System.nanoTime());
        body.put("title", "IT工作流测试");
        body.put("formFields", Map.of("reason", "集成测试"));
        ResponseEntity<Result> resp = post("/api/workflow/instances/start", body, Result.class, token);
        assertEquals(200, resp.getStatusCode().value(), "发起流程应成功: " + resp.getBody());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess());
        return (String) resp.getBody().data();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> myTasks(String token) {
        ResponseEntity<Result> resp = get("/api/workflow/tasks/mine", Result.class, token);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        return (List<Map<String, Object>>) resp.getBody().data();
    }

    /** 在我的待办里找到属于指定实例的任务。 */
    private Map<String, Object> myTaskForInstance(String token, String instanceId) {
        return myTasks(token).stream()
                .filter(t -> instanceId.equals(t.get("processInstanceId")))
                .findFirst().orElse(null);
    }

    private String taskIdOf(Map<String, Object> task) {
        return (String) task.get("taskId");
    }

    private ResponseEntity<Result> completeTask(String token, String taskId, String action,
                                                String operationId, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", taskId);
        body.put("action", action);
        body.put("opinion", "IT测试意见");
        body.put("operationId", operationId);
        HttpHeaders h = bearer(token);
        if (idempotencyKey != null) {
            h.set("Idempotency-Key", idempotencyKey);
        }
        return rest.exchange(baseUrl() + "/api/workflow/tasks/complete", HttpMethod.POST,
                new HttpEntity<>(body, h), Result.class);
    }

    private ResponseEntity<Result> transferTask(String token, String taskId, Long toUserId,
                                                String operationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", taskId);
        body.put("toUserId", toUserId);
        body.put("opinion", "IT转办");
        body.put("operationId", operationId);
        HttpHeaders h = bearer(token);
        h.set("Idempotency-Key", operationId);
        return rest.exchange(baseUrl() + "/api/workflow/tasks/transfer", HttpMethod.POST,
                new HttpEntity<>(body, h), Result.class);
    }

    private ResponseEntity<Result> claimTask(String token, String taskId) {
        return rest.exchange(baseUrl() + "/api/workflow/tasks/" + taskId + "/claim", HttpMethod.POST,
                new HttpEntity<>(new LinkedHashMap<String, Object>(), bearer(token)), Result.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> approvalRecords(String token, String instanceId) {
        ResponseEntity<Result> resp = get("/api/workflow/instances/" + instanceId + "/records",
                Result.class, token);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        return (List<Map<String, Object>>) resp.getBody().data();
    }

    // ==================== D-5 回归：超管访问权 ====================

    /**
     * D-5 回归：超级管理员可访问工作流定义列表与任务认领端点。
     *
     * <p>V11 之后新增的 workflow:definition:* / workflow:task:claim 权限码从未授予
     * SUPER_ADMIN（sync 只登记资源不授权），超管访问一律 403；V28 补授后恢复。
     * 认领端点用不存在的 taskId 探测：有权限时进入服务层返回 404，无权限时 403。
     */
    @Test
    void superAdminCanAccessWorkflowApis() {
        ResponseEntity<Result> defs = get("/api/workflow/definitions", Result.class, adminToken());
        assertEquals(200, defs.getStatusCode().value(), "超管应可访问工作流定义列表（D-5 回归）");
        assertNotNull(defs.getBody());
        assertTrue(defs.getBody().isSuccess());

        ResponseEntity<Result> claim = claimTask(adminToken(), "NO_SUCH_TASK");
        assertEquals(404, claim.getStatusCode().value(),
                "认领端点应可达（权限通过后由服务层返回 404，而非 403）");
    }

    // ==================== 审批守卫与闭环 ====================

    /** 异常：审批缺少 Idempotency-Key → 400（强制幂等键，防重复审批）。 */
    @Test
    void completeWithoutIdempotencyKey_returns400() {
        String token = adminToken();
        String instanceId = startProcess(token);
        Map<String, Object> task = myTaskForInstance(token, instanceId);
        assertNotNull(task, "发起后应存在分派给自己的待办");

        ResponseEntity<Result> resp = completeTask(token, taskIdOf(task), "APPROVE",
                "op-" + System.nanoTime(), null);
        assertEquals(400, resp.getStatusCode().value(), "缺少幂等键应返回 400");
    }

    /** 异常：非任务处理人审批 → 403（服务级守卫先于引擎操作）。 */
    @Test
    void nonAssigneeCannotComplete_returns403() {
        String admin = adminToken();
        String instanceId = startProcess(admin);
        Map<String, Object> task = myTaskForInstance(admin, instanceId);
        assertNotNull(task, "发起后应存在分派给自己的待办");

        // 另一名持全部权限码的用户（非处理人）尝试审批
        String otherName = createSuperAdminUser("other");
        String otherToken = loginAs(otherName, "Reset123!");
        ResponseEntity<Result> resp = completeTask(otherToken, taskIdOf(task), "APPROVE",
                "op-" + System.nanoTime(), "key-" + System.nanoTime());
        assertEquals(403, resp.getStatusCode().value(), "非处理人审批应返回 403");
    }

    /** 正常：已是自己的任务重复认领 → 幂等成功（无副作用）。 */
    @Test
    void claimAlreadyMine_isIdempotent() {
        String token = adminToken();
        String instanceId = startProcess(token);
        Map<String, Object> task = myTaskForInstance(token, instanceId);
        assertNotNull(task, "发起后应存在分派给自己的待办");

        ResponseEntity<Result> first = claimTask(token, taskIdOf(task));
        assertEquals(200, first.getStatusCode().value(), "认领自己的任务应成功");
        ResponseEntity<Result> second = claimTask(token, taskIdOf(task));
        assertEquals(200, second.getStatusCode().value(), "重复认领应幂等成功");
        assertNotNull(myTaskForInstance(token, instanceId), "认领后任务仍在我的待办中");
    }

    /** 正常+闭环：转办 → 新处理人审批 → 记录含 TRANSFER + APPROVE。 */
    @Test
    void transferThenComplete_recordsBothActions() {
        String admin = adminToken();
        String instanceId = startProcess(admin);
        Map<String, Object> task = myTaskForInstance(admin, instanceId);
        assertNotNull(task, "发起后应存在分派给自己的待办");
        String taskId = taskIdOf(task);

        String otherName = createSuperAdminUser("transferee");
        String otherToken = loginAs(otherName, "Reset123!");
        Long otherId = createdUserIds.get(createdUserIds.size() - 1);

        String transferOp = "op-transfer-" + System.nanoTime();
        ResponseEntity<Result> transferResp = transferTask(admin, taskId, otherId, transferOp);
        assertEquals(200, transferResp.getStatusCode().value(), "转办应成功: " + transferResp.getBody());

        // 原处理人不再持有该任务，新处理人待办出现
        assertNotNull(myTaskForInstance(otherToken, instanceId), "转办后新处理人应收到待办");

        String completeOp = "op-complete-" + System.nanoTime();
        ResponseEntity<Result> completeResp = completeTask(otherToken, taskId, "APPROVE",
                completeOp, "key-" + System.nanoTime());
        assertEquals(200, completeResp.getStatusCode().value(), "新处理人审批应成功: " + completeResp.getBody());

        List<Map<String, Object>> records = approvalRecords(admin, instanceId);
        List<String> actions = records.stream().map(r -> String.valueOf(r.get("action"))).toList();
        assertTrue(actions.contains("TRANSFER"), "审批记录应包含转办动作");
        assertTrue(actions.contains("APPROVE"), "审批记录应包含审批动作");
    }

    /** 边界：同一 operationId 重复审批幂等，不产生第二条审批记录。 */
    @Test
    void duplicateComplete_isIdempotent() {
        String token = adminToken();
        String instanceId = startProcess(token);
        Map<String, Object> task = myTaskForInstance(token, instanceId);
        assertNotNull(task, "发起后应存在分派给自己的待办");

        String opId = "op-dup-" + System.nanoTime();
        String key = "key-" + System.nanoTime();
        ResponseEntity<Result> first = completeTask(token, taskIdOf(task), "APPROVE", opId, key);
        assertEquals(200, first.getStatusCode().value(), "首次审批应成功: " + first.getBody());
        long afterFirst = approvalRecords(token, instanceId).size();

        ResponseEntity<Result> second = completeTask(token, taskIdOf(task), "APPROVE", opId, key);
        assertEquals(200, second.getStatusCode().value(), "重复审批应幂等返回成功");
        assertEquals(afterFirst, approvalRecords(token, instanceId).size(),
                "重复审批不得产生新的审批记录");
    }
}
