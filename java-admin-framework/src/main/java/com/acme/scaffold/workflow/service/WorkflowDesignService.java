package com.acme.scaffold.workflow.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.workflow.assignee.AssigneeResolver;
import com.acme.scaffold.workflow.bpmn.BpmnWorkflowBuilder;
import com.acme.scaffold.workflow.dto.design.CreateDesignRequest;
import com.acme.scaffold.workflow.dto.design.SaveDesignRequest;
import com.acme.scaffold.workflow.dto.design.WorkflowDesignView;
import com.acme.scaffold.workflow.dto.design.WorkflowEdgeDTO;
import com.acme.scaffold.workflow.dto.design.WorkflowNodeDTO;
import com.acme.scaffold.workflow.entity.WfWorkflowDesignDO;
import com.acme.scaffold.workflow.model.WorkflowNodeType;
import com.acme.scaffold.workflow.port.WorkflowEnginePort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义工作流设计服务：管理「草稿 -> 发布 -> 取消发布 -> 再编辑」全生命周期。
 *
 * <p>发布时把节点/边图经 {@link BpmnWorkflowBuilder} 编译为 BPMN 2.0 XML，
 * 通过引擎端口部署为新版本流程定义（同一 processKey 多次发布形成版本链），
 * 并将节点/边/BPMN/部署信息写入 {@code wf_workflow_design}（单一可信源）。
 * 发起实例时按 processKey 找到已发布设计，用 {@link #buildStartVariables} 解析各审批节点受让人、
 * 计算会签阈值并注入业务表单字段，实现「改完发布即时对新发起实例生效，在途实例不受影响」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowDesignService {

    private final WorkflowEnginePort engine;
    private final DSLContext dsl;
    private final AssigneeResolver assigneeResolver;
    private final SecurityContextFacade securityContextFacade;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---------- 草稿 CRUD ----------

    @Transactional
    public WorkflowDesignView create(CreateDesignRequest req) {
        WfWorkflowDesignDO existing = JooqWriters.fetchOne(dsl, JooqTables.WF_WORKFLOW_DESIGN, WfWorkflowDesignDO.class,
                JooqTables.WF_WORKFLOW_DESIGN.field("process_key", String.class).eq(req.processKey()));
        if (existing != null) {
            return get(existing.getId());
        }
        WfWorkflowDesignDO e = new WfWorkflowDesignDO();
        e.setProcessKey(req.processKey());
        e.setProcessName(req.processName());
        e.setDescription(req.description());
        e.setStatus("DRAFT");
        e.setVersion(0);
        e.setNodesJson("[]");
        e.setEdgesJson("[]");
        JooqWriters.insert(dsl, JooqTables.WF_WORKFLOW_DESIGN, e);
        return get(e.getId());
    }

    @Transactional
    public WorkflowDesignView save(Long id, SaveDesignRequest req) {
        WfWorkflowDesignDO e = requireDraft(id);
        e.setProcessName(req.processName());
        e.setDescription(req.description());
        e.setFormSchema(req.formSchema());
        e.setNodesJson(toJson(req.nodes()));
        e.setEdgesJson(toJson(req.edges()));
        JooqWriters.updateById(dsl, JooqTables.WF_WORKFLOW_DESIGN, id, e);
        return get(id);
    }

    public WorkflowDesignView get(Long id) {
        WfWorkflowDesignDO e = JooqWriters.fetchById(dsl, JooqTables.WF_WORKFLOW_DESIGN, WfWorkflowDesignDO.class, id);
        if (e == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "工作流设计不存在: " + id);
        }
        return toView(e);
    }

    public List<WorkflowDesignView> list() {
        return JooqWriters.fetchList(dsl, JooqTables.WF_WORKFLOW_DESIGN, WfWorkflowDesignDO.class,
                        JooqWriters.notDeleted(JooqTables.WF_WORKFLOW_DESIGN),
                        JooqTables.WF_WORKFLOW_DESIGN.field("id", Long.class).desc())
                .stream().map(this::toView).toList();
    }

    @Transactional
    public void delete(Long id) {
        requireDraft(id);
        JooqWriters.delete(dsl, JooqTables.WF_WORKFLOW_DESIGN, id, true);
    }

    // ---------- 发布 / 取消发布 ----------

    @Transactional
    public WorkflowDesignView publish(Long id) {
        WfWorkflowDesignDO e = JooqWriters.fetchById(dsl, JooqTables.WF_WORKFLOW_DESIGN, WfWorkflowDesignDO.class, id);
        if (e == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "工作流设计不存在: " + id);
        }
        List<WorkflowNodeDTO> nodes = parseNodes(e.getNodesJson());
        List<WorkflowEdgeDTO> edges = parseEdges(e.getEdgesJson());
        // 发布前校验审批节点受让人配置可解析（角色/部门存在性），避免发布后发起时静默失效
        for (WorkflowNodeDTO node : nodes) {
            if (node.type() == WorkflowNodeType.APPROVAL) {
                assigneeResolver.validateConfig(node);
            }
        }
        String bpmn = BpmnWorkflowBuilder.build(e.getProcessKey(), e.getProcessName(), nodes, edges);
        String deploymentId = engine.deploy(e.getProcessKey(), e.getProcessName(), bpmn);
        String defId = engine.latestProcessDefinitionId(e.getProcessKey());

        e.setVersion(e.getVersion() == null ? 1 : e.getVersion() + 1);
        e.setStatus("PUBLISHED");
        e.setBpmnXml(bpmn);
        e.setDeploymentId(deploymentId);
        e.setProcessDefinitionId(defId);
        CurrentPrincipal principal = securityContextFacade.getCurrentPrincipal().orElse(null);
        e.setPublishedBy(principal == null ? null : principal.userId());
        e.setPublishedAt(LocalDateTime.now());
        JooqWriters.updateById(dsl, JooqTables.WF_WORKFLOW_DESIGN, id, e);
        log.info("工作流发布成功 processKey={} version={} deploymentId={}", e.getProcessKey(), e.getVersion(), deploymentId);
        return get(id);
    }

    @Transactional
    public WorkflowDesignView unpublish(Long id) {
        WfWorkflowDesignDO e = requirePublished(id);
        e.setStatus("DRAFT");
        JooqWriters.updateById(dsl, JooqTables.WF_WORKFLOW_DESIGN, id, e);
        return get(id);
    }

    // ---------- 发起时变量构建 ----------

    public WorkflowDesignView getPublished(String processKey) {
        WfWorkflowDesignDO e = JooqWriters.fetchOne(dsl, JooqTables.WF_WORKFLOW_DESIGN, WfWorkflowDesignDO.class,
                JooqTables.WF_WORKFLOW_DESIGN.field("process_key", String.class).eq(processKey)
                        .and(JooqTables.WF_WORKFLOW_DESIGN.field("status", String.class).eq("PUBLISHED")));
        return e == null ? null : toView(e);
    }

    /**
     * 从已发布设计构建流程发起变量：按节点解析受让人、计算会签阈值，并合并业务表单字段。
     * 每个 APPROVAL 节点注入 {@code node_<id>_assignees}（受让人列表）、
     * {@code node_<id>_threshold}（完成阈值）、{@code rejected_<id>}（驳回标记，默认 false）。
     */
    public Map<String, Object> buildStartVariables(WorkflowDesignView design, Long starterUserId, Long starterOrgId,
                                                   Map<String, Object> formFields) {
        Map<String, Object> vars = new HashMap<>();
        if (formFields != null) {
            vars.putAll(formFields);
        }
        for (WorkflowNodeDTO node : design.nodes()) {
            WorkflowNodeType type = node.type();
            if (type != WorkflowNodeType.APPROVAL
                    && type != WorkflowNodeType.CC
                    && type != WorkflowNodeType.SERVICE) {
                continue;
            }
            List<Long> assignees = assigneeResolver.resolve(node, starterUserId, starterOrgId);
            String suffix = node.id();
            if (type == WorkflowNodeType.APPROVAL) {
                // 审批节点受让人为空属于配置错误，必须拦截（否则流程会跳过/卡死该节点）
                if (assignees.isEmpty()) {
                    String nodeName = node.name() != null ? node.name() : node.id();
                    throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                            "审批节点「" + nodeName + "」未解析出任何有效审批人，请检查该节点的受让人配置"
                                    + "（发起人主管/角色/用户/部门），确保对应组织对应用户存在。");
                }
                int size = assignees.size();
                int threshold = computeThreshold(node, size);
                vars.put("node_" + suffix + "_assignees", assignees.stream().map(String::valueOf).toList());
                vars.put("node_" + suffix + "_threshold", threshold);
                vars.put("rejected_" + suffix, false);
            } else {
                // CC / SERVICE 节点：注入受让人供委托类读取（空则委托类告警跳过，不阻断流程）
                vars.put("node_" + suffix + "_assignees", assignees.stream().map(String::valueOf).toList());
            }
        }
        return vars;
    }

    // ---------- 私有辅助 ----------

    private int computeThreshold(WorkflowNodeDTO node, int size) {
        if (size <= 0) {
            return 0;
        }
        String mode = node.approvalMode() == null ? "ANY" : node.approvalMode().toUpperCase();
        return switch (mode) {
            case "ALL" -> size;
            case "RATIO" -> {
                BigDecimal ratio = node.approvalRatio() == null ? BigDecimal.ONE : node.approvalRatio();
                yield (int) Math.ceil(size * ratio.doubleValue());
            }
            default -> 1; // ANY
        };
    }

    private WfWorkflowDesignDO requireDraft(Long id) {
        WfWorkflowDesignDO e = JooqWriters.fetchById(dsl, JooqTables.WF_WORKFLOW_DESIGN, WfWorkflowDesignDO.class, id);
        if (e == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "工作流设计不存在: " + id);
        }
        if (!"DRAFT".equals(e.getStatus())) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "仅草稿态可编辑（已发布请先取消发布）");
        }
        return e;
    }

    private WfWorkflowDesignDO requirePublished(Long id) {
        WfWorkflowDesignDO e = JooqWriters.fetchById(dsl, JooqTables.WF_WORKFLOW_DESIGN, WfWorkflowDesignDO.class, id);
        if (e == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "工作流设计不存在: " + id);
        }
        if (!"PUBLISHED".equals(e.getStatus())) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "该设计未发布");
        }
        return e;
    }

    private WorkflowDesignView toView(WfWorkflowDesignDO e) {
        return toView(e, parseNodes(e.getNodesJson()), parseEdges(e.getEdgesJson()));
    }

    private WorkflowDesignView toView(WfWorkflowDesignDO e, List<WorkflowNodeDTO> nodes, List<WorkflowEdgeDTO> edges) {
        return new WorkflowDesignView(e.getId(), e.getProcessKey(), e.getProcessName(), e.getDescription(),
                e.getStatus(), e.getVersion(), e.getFormSchema(), nodes, edges, e.getBpmnXml(),
                e.getDeploymentId(), e.getProcessDefinitionId(), e.getPublishedAt(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private List<WorkflowNodeDTO> parseNodes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<WorkflowNodeDTO>>() {
            });
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "节点 JSON 解析失败: " + ex.getMessage());
        }
    }

    private List<WorkflowEdgeDTO> parseEdges(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<WorkflowEdgeDTO>>() {
            });
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "连线 JSON 解析失败: " + ex.getMessage());
        }
    }

    private String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "JSON 序列化失败: " + ex.getMessage());
        }
    }
}
