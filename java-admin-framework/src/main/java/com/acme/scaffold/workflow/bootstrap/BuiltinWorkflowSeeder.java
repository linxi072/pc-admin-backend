package com.acme.scaffold.workflow.bootstrap;

import com.acme.scaffold.workflow.dto.design.CreateDesignRequest;
import com.acme.scaffold.workflow.dto.design.SaveDesignRequest;
import com.acme.scaffold.workflow.dto.design.WorkflowDesignView;
import com.acme.scaffold.workflow.dto.design.WorkflowEdgeDTO;
import com.acme.scaffold.workflow.dto.design.WorkflowNodeDTO;
import com.acme.scaffold.workflow.model.WorkflowNodeType;
import com.acme.scaffold.workflow.service.WorkflowDesignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 内置工作流种子：将原先 classpath 下的静态 BPMN（leaveApproval.bpmn20.xml）改为以
 * {@code wf_workflow_design} 单一可信源驱动的「可编辑设计」。
 *
 * <p>启动后若库内尚无已发布的 {@code leaveApproval} 设计，则创建草稿、写入内置节点/连线并发布，
 * 之后即可在「工作流设计」后台二次编辑节点顺序、受让人、驳回策略并一键发布即时生效。
 * 若用户已取消发布/编辑过（节点非空或版本>0），则尊重其设计，不再覆盖。</p>
 */
@Slf4j
@Component
@Order(200)
@RequiredArgsConstructor
public class BuiltinWorkflowSeeder implements ApplicationRunner {

    /** 内置请假流程的 processKey，与原静态 BPMN 的 process id 保持一致。 */
    public static final String LEAVE_APPROVAL_KEY = "leaveApproval";

    private final WorkflowDesignService designService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedLeaveApproval();
        } catch (Exception e) {
            // 种子初始化失败不应阻塞应用启动（如本机尚未初始化库）
            log.error("内置工作流种子初始化失败（不影响启动）: {}", e.getMessage(), e);
        }
    }

    @Transactional
    void seedLeaveApproval() {
        if (designService.getPublished(LEAVE_APPROVAL_KEY) != null) {
            return;
        }
        WorkflowDesignView draft = designService.create(
                new CreateDesignRequest(LEAVE_APPROVAL_KEY, "请假审批（含会签/或签/驳回）",
                        "内置请假流程，已改造为可编辑设计；受让人/驳回策略可在设计器中调整。"));
        if (draft == null) {
            return;
        }
        boolean pristine = (draft.nodes() == null || draft.nodes().isEmpty())
                && (draft.version() == null || draft.version() == 0);
        if (pristine) {
            designService.save(draft.id(), buildLeaveApprovalDesign(draft.processName()));
            designService.publish(draft.id());
            log.info("内置工作流 {} 已作为可编辑设计发布（v{}）", LEAVE_APPROVAL_KEY, draft.version() + 1);
        }
        // 否则：用户已存在该 key 的设计（草稿/已编辑），尊重现状，不覆盖
    }

    /**
     * 构造内置请假审批的设计（与历史静态 BPMN 语义一致）：
     * 发起 → 会签/或签(ALL) → 主管审批(ALL) → 审批通过；任一审批节点驳回即终止（rejectEnd）。
     *
     * <p>默认受让人写死为用户 id=1（种子 admin），仅作占位；生产环境请在设计器中改为真实审批人/角色。
     * 节点 id 使用稳定可读命名，便于在设计器中引用与条件分支配置。</p>
     */
    public static SaveDesignRequest buildLeaveApprovalDesign(String processName) {
        List<WorkflowNodeDTO> nodes = List.of(
                new WorkflowNodeDTO("start", WorkflowNodeType.START, "发起",
                        null, null, null, null, null, null, null, null, null, null, null),
                new WorkflowNodeDTO("countersign", WorkflowNodeType.APPROVAL, "会签/或签", "ALL", null,
                        "USER", "1", null, null, null, "END", null, null, null),
                new WorkflowNodeDTO("manager", WorkflowNodeType.APPROVAL, "主管审批", "ALL", null,
                        "USER", "1", null, null, null, "END", null, null, null),
                new WorkflowNodeDTO("approvedEnd", WorkflowNodeType.END, "审批通过",
                        null, null, null, null, null, null, null, null, null, null, null)
        );
        List<WorkflowEdgeDTO> edges = List.of(
                new WorkflowEdgeDTO("e1", "start", "countersign", null, false),
                new WorkflowEdgeDTO("e2", "countersign", "manager", null, false),
                new WorkflowEdgeDTO("e3", "manager", "approvedEnd", null, false)
        );
        String formSchema = "[{\"field\":\"leaveType\",\"label\":\"请假类型\",\"type\":\"text\"},"
                + "{\"field\":\"days\",\"label\":\"请假天数\",\"type\":\"number\"},"
                + "{\"field\":\"reason\",\"label\":\"事由\",\"type\":\"textarea\"}]";
        return new SaveDesignRequest(processName,
                "内置请假审批：会签/或签 -> 主管审批 -> 结束；任一节点驳回即终止。",
                formSchema, nodes, edges);
    }
}
