package com.acme.scaffold.workflow.approval.application;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.workflow.definition.dto.design.WorkflowDesignView;
import com.acme.scaffold.workflow.instance.application.WorkflowService;
import com.acme.scaffold.workflow.instance.dto.CompleteTaskRequest;
import com.acme.scaffold.workflow.instance.dto.DelegateTaskRequest;
import com.acme.scaffold.workflow.instance.dto.ResolveDelegateRequest;
import com.acme.scaffold.workflow.instance.dto.StartProcessRequest;
import com.acme.scaffold.workflow.instance.dto.TransferTaskRequest;
import com.acme.scaffold.workflow.support.ApprovalAction;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

/**
 * 审批中心动作门面：把门户从「只读聚合」补齐为可操作的审批工作台。
 *
 * <p>定位为薄编排层：所有写操作委托 {@link WorkflowService}（引擎行为与幂等语义的唯一来源），
 * 本层只补门户视角的业务边界，不重复实现工作流引擎：</p>
 * <ul>
 *   <li>发起：processKey 须属于门户「可发起」列表中的已发布流程，杜绝发起草稿/停用流程；</li>
 *   <li>审批：仅接受 APPROVE/REJECT，TRANSFER 必须走转办端点——枚举中的 TRANSFER 若直达
 *       complete 会被当作普通通过，属于静默的业务语义错误；</li>
 *   <li>动作参数：taskId / operationId / 目标用户前置非空校验，避免脏数据进入引擎；
 *       Controller 的 {@code @Valid} 只覆盖 HTTP 入口，Service 层校验同时保护内部调用与单测。</li>
 * </ul>
 *
 * <p>幂等语义沿用 {@code WorkflowService}：审批/转办/委派/办结委派强制要求
 * {@code Idempotency-Key} 请求头（{@code @Idempotent(requireKey=true)}），
 * 且 body 的 {@code operationId} 承担引擎层二次去重；认领天然幂等，不强制请求头。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalActionService {

    private final ApprovalPortalService portalService;
    private final WorkflowService workflowService;

    /**
     * 发起流程：仅允许门户列出的「可发起（PUBLISHED）」流程。
     *
     * <p>空数据兜底：设计中心尚未初始化任何已发布设计时放行，委托
     * {@code WorkflowService#start} 的遗留变量契约兜底，避免审批工作台在空环境完全不可用。</p>
     */
    public String start(StartProcessRequest request) {
        requireText(request.processKey(), "流程 Key");
        requireText(request.businessType(), "业务类型");
        requireText(request.businessId(), "业务 ID");
        requireText(request.title(), "流程标题");
        List<WorkflowDesignView> startable = portalService.startable();
        if (startable.isEmpty()) {
            log.warn("审批中心未发现已发布流程设计，放行兜底发起 processKey={}", request.processKey());
        } else if (startable.stream().noneMatch(v -> request.processKey().equals(v.processKey())
                && "PUBLISHED".equals(v.status()))) {
            // 除比对 processKey 外，再确认设计状态确为 PUBLISHED：门户聚合层理论上已过滤，
            // 但门面作为写入口须自行守门，避免上游列表混入草稿/停用时被当作可发起流程。
            throw BusinessException.badRequest("流程未发布或不可从审批中心发起: " + request.processKey());
        }
        return workflowService.start(request);
    }

    /** 审批/驳回任务。 */
    public void approve(CompleteTaskRequest request) {
        requireText(request.taskId(), "任务 ID");
        requireText(request.operationId(), "operationId");
        if (request.action() == null) {
            throw BusinessException.badRequest("审批动作不能为空");
        }
        if (request.action() == ApprovalAction.TRANSFER) {
            throw BusinessException.badRequest("转办请使用 /api/approval/actions/transfer，不能作为审批动作提交");
        }
        workflowService.completeTask(request);
    }

    /** 转办任务。 */
    public void transfer(TransferTaskRequest request) {
        requireText(request.taskId(), "任务 ID");
        requireText(request.operationId(), "operationId");
        if (request.toUserId() == null) {
            throw BusinessException.badRequest("转办目标用户不能为空");
        }
        workflowService.transfer(request);
    }

    /** 委派任务（临时委托他人办理，办结后交还原审批人）。 */
    public void delegate(DelegateTaskRequest request) {
        requireText(request.taskId(), "任务 ID");
        requireText(request.operationId(), "operationId");
        if (request.toUserId() == null) {
            throw BusinessException.badRequest("委派目标用户不能为空");
        }
        workflowService.delegate(request);
    }

    /** 办结委派（被委托人交还原审批人）。 */
    public void resolveDelegate(ResolveDelegateRequest request) {
        requireText(request.taskId(), "任务 ID");
        requireText(request.operationId(), "operationId");
        workflowService.resolveDelegate(request);
    }

    /** 认领池化任务（尚未指定办理人）。 */
    public void claim(String taskId) {
        requireText(taskId, "任务 ID");
        workflowService.claim(taskId);
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw BusinessException.badRequest(label + "不能为空");
        }
    }
}
