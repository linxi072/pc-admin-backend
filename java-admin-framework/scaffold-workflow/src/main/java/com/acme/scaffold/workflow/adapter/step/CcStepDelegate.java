package com.acme.scaffold.workflow.adapter.step;

import com.acme.scaffold.system.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义工作流「抄送(CC)」节点委托：流程流转到该节点时，向配置的抄送人发送站内信通知，不阻塞流程。
 *
 * <p>抄送人由设计器配置，随流程发起时经 {@code WorkflowDesignService#buildStartVariables} 注入
 * 变量 {@code node_<id>_assignees}（多对多动态解析，支持发起人主管/角色/用户/部门）。本委托读取该变量并调用
 * {@link MessageService#sendCcNotification} 投递，使抄送节点真正可观测，而非仅打日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CcStepDelegate implements JavaDelegate {

    private final MessageService messageService;

    @Override
    public void execute(DelegateExecution execution) {
        String nodeId = execution.getCurrentActivityId();
        String processInstanceId = execution.getProcessInstanceId();
        List<Long> ccUserIds = parseUserIds(execution.getVariable("node_" + nodeId + "_assignees"));
        if (ccUserIds.isEmpty()) {
            log.warn("[工作流抄送] 节点={} 未配置抄送人，跳过。processInstanceId={}", nodeId, processInstanceId);
            execution.setVariable("cc_" + nodeId + "_done", true);
            return;
        }
        try {
            int sent = messageService.sendCcNotification(ccUserIds,
                    "流程抄送通知", "您被抄送了一条流程，流程实例ID：" + processInstanceId);
            log.info("[工作流抄送] 节点={} 已抄送 {} 人 processInstanceId={}", nodeId, sent, processInstanceId);
        } catch (Exception e) {
            // 抄送失败不应阻断主流程流转
            log.error("[工作流抄送] 发送抄送通知失败，节点={} processInstanceId={}", nodeId, processInstanceId, e);
        }
        execution.setVariable("cc_" + nodeId + "_done", true);
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseUserIds(Object raw) {
        List<Long> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Long l) {
                    result.add(l);
                } else if (o instanceof String s && !s.isBlank()) {
                    try {
                        result.add(Long.parseLong(s));
                    } catch (NumberFormatException ignored) {
                        // 忽略非数字元素
                    }
                } else if (o instanceof Number n) {
                    result.add(n.longValue());
                }
            }
        }
        return result;
    }
}
