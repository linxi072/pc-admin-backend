package com.acme.scaffold.approval;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.workflow.dto.CompleteTaskRequest;
import com.acme.scaffold.workflow.dto.DelegateTaskRequest;
import com.acme.scaffold.workflow.dto.ResolveDelegateRequest;
import com.acme.scaffold.workflow.dto.StartProcessRequest;
import com.acme.scaffold.workflow.dto.TransferTaskRequest;
import com.acme.scaffold.workflow.dto.design.WorkflowDesignView;
import com.acme.scaffold.workflow.model.ApprovalAction;
import com.acme.scaffold.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalActionService} 编排单测：验证「动作门面」在委托引擎前补齐的业务边界，
 * 以及各动作确实落到 {@link WorkflowService} 对应方法（不触库、不加载 Spring 上下文）。
 */
class ApprovalActionServiceTest {

    private ApprovalPortalService portalService;
    private WorkflowService workflowService;
    private ApprovalActionService actionService;

    @BeforeEach
    void setUp() {
        portalService = mock(ApprovalPortalService.class);
        workflowService = mock(WorkflowService.class);
        actionService = new ApprovalActionService(portalService, workflowService);
    }

    private WorkflowDesignView design(String key, String status) {
        return new WorkflowDesignView(null, key, "流程" + key, null, status, 1,
                null, null, null, null, null, null, null, null, null);
    }

    private StartProcessRequest startReq(String processKey) {
        return new StartProcessRequest(processKey, "leave", "B-1", "请假申请",
                null, null, null, Map.of());
    }

    @Test
    @DisplayName("start：可发起列表命中已发布流程时委托引擎并返回实例 ID")
    void start_published_delegates() {
        when(portalService.startable()).thenReturn(List.of(
                design("leave", "PUBLISHED"), design("expense", "PUBLISHED")));
        when(workflowService.start(any())).thenReturn("pi-1");
        StartProcessRequest req = startReq("expense");

        assertEquals("pi-1", actionService.start(req));
        verify(workflowService).start(req);
    }

    @Test
    @DisplayName("start：草稿/停用/未知 processKey 被拒，不进入引擎")
    void start_unpublished_rejected() {
        when(portalService.startable()).thenReturn(List.of(
                design("leave", "PUBLISHED"), design("draft", "DRAFT")));

        assertThrows(BusinessException.class, () -> actionService.start(startReq("draft")));
        assertThrows(BusinessException.class, () -> actionService.start(startReq("unknown")));
        verify(workflowService, never()).start(any());
    }

    @Test
    @DisplayName("start：设计中心无已发布流程时放行兜底，保证空环境可用")
    void start_emptyCatalog_fallsBack() {
        when(portalService.startable()).thenReturn(List.of());
        when(workflowService.start(any())).thenReturn("pi-fallback");

        assertEquals("pi-fallback", actionService.start(startReq("legacy")));
        verify(workflowService).start(any());
    }

    @Test
    @DisplayName("start：必填字段缺失被拒")
    void start_blankFields_rejected() {
        when(portalService.startable()).thenReturn(List.of(design("leave", "PUBLISHED")));

        assertThrows(BusinessException.class, () -> actionService.start(startReq("  ")));
        assertThrows(BusinessException.class, () -> actionService.start(
                new StartProcessRequest("leave", "", "B-1", "标题", null, null, null, Map.of())));
        verify(workflowService, never()).start(any());
    }

    @Test
    @DisplayName("approve：APPROVE / REJECT 均委托 completeTask")
    void approve_validActions_delegates() {
        CompleteTaskRequest approve = new CompleteTaskRequest("t1", ApprovalAction.APPROVE, "同意", "op-1");
        CompleteTaskRequest reject = new CompleteTaskRequest("t2", ApprovalAction.REJECT, "不同意", "op-2");

        actionService.approve(approve);
        actionService.approve(reject);

        verify(workflowService).completeTask(approve);
        verify(workflowService).completeTask(reject);
    }

    @Test
    @DisplayName("approve：TRANSFER 不得作为审批动作（否则会被静默当作通过）")
    void approve_transfer_rejected() {
        CompleteTaskRequest req = new CompleteTaskRequest("t1", ApprovalAction.TRANSFER, "转办", "op-1");

        assertThrows(BusinessException.class, () -> actionService.approve(req));
        verify(workflowService, never()).completeTask(any());
    }

    @Test
    @DisplayName("approve：动作/任务 ID/幂等键缺失被拒")
    void approve_missingFields_rejected() {
        assertThrows(BusinessException.class, () -> actionService.approve(
                new CompleteTaskRequest("t1", null, "", "op-1")));
        assertThrows(BusinessException.class, () -> actionService.approve(
                new CompleteTaskRequest(" ", ApprovalAction.APPROVE, "", "op-1")));
        assertThrows(BusinessException.class, () -> actionService.approve(
                new CompleteTaskRequest("t1", ApprovalAction.APPROVE, "", " ")));
        verify(workflowService, never()).completeTask(any());
    }

    @Test
    @DisplayName("transfer/delegate/resolveDelegate/claim：按动作委托到引擎对应方法")
    void actions_delegate() {
        TransferTaskRequest transfer = new TransferTaskRequest("t1", 2L, "转办", "op-1");
        DelegateTaskRequest delegate = new DelegateTaskRequest("t2", 3L, "委派", "op-2");
        ResolveDelegateRequest resolve = new ResolveDelegateRequest("t3", "办结", "op-3");

        actionService.transfer(transfer);
        actionService.delegate(delegate);
        actionService.resolveDelegate(resolve);
        actionService.claim("t4");

        verify(workflowService).transfer(transfer);
        verify(workflowService).delegate(delegate);
        verify(workflowService).resolveDelegate(resolve);
        verify(workflowService).claim("t4");
    }

    @Test
    @DisplayName("transfer/delegate：目标用户缺失被拒，不进入引擎")
    void transfer_missingTarget_rejected() {
        assertThrows(BusinessException.class, () -> actionService.transfer(
                new TransferTaskRequest("t1", null, "", "op-1")));
        assertThrows(BusinessException.class, () -> actionService.delegate(
                new DelegateTaskRequest("t2", null, "", "op-2")));
        verify(workflowService, never()).transfer(any());
        verify(workflowService, never()).delegate(any());
    }

    @Test
    @DisplayName("claim：空白 taskId 被拒，不进入引擎")
    void claim_blankTaskId_rejected() {
        assertThrows(BusinessException.class, () -> actionService.claim(" "));
        assertThrows(BusinessException.class, () -> actionService.claim(null));
        verify(workflowService, never()).claim(any());
    }
}
