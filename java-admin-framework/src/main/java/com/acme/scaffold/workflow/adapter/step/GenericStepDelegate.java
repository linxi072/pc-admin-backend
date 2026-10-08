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
 * 自定义工作流「执行步骤(SERVICE)」节点委托：流程流转到该节点时自动执行。
 *
 * <p>具体业务动作（如调用外部系统、写业务数据）请在此扩展点接入；本实现落地为两件事，使其不再仅是占位日志：
 * <ol>
 *   <li>结构化记录节点、流程实例与业务单据（businessKey），便于审计与排查；</li>
 *   <li>若节点配置了「关注人（assignee）」，向其发送执行完成通知，使自动步骤可观测。</li>
 * </ol>
 * 关注人随流程发起时经 {@code WorkflowDesignService#buildStartVariables} 注入变量 {@code node_<id>_assignees}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenericStepDelegate implements JavaDelegate {

    private final MessageService messageService;

    @Override
    public void execute(DelegateExecution execution) {
        String nodeId = execution.getCurrentActivityId();
        String processInstanceId = execution.getProcessInstanceId();
        String businessKey = execution.getProcessInstanceBusinessKey();
        log.info("[工作流执行步骤] 节点={} processInstanceId={} businessKey={}",
                nodeId, processInstanceId, businessKey);
        // 扩展点：在此调用外部服务 / 落库步骤执行记录。

        List<Long> watchers = parseUserIds(execution.getVariable("node_" + nodeId + "_assignees"));
        if (!watchers.isEmpty()) {
            try {
                int sent = messageService.sendCcNotification(watchers,
                        "执行步骤已完成",
                        "流程执行步骤「" + nodeId + "」已完成，流程实例ID：" + processInstanceId);
                log.info("[工作流执行步骤] 已通知 {} 名关注人 节点={} processInstanceId={}",
                        sent, nodeId, processInstanceId);
            } catch (Exception e) {
                log.error("[工作流执行步骤] 发送执行通知失败，节点={} processInstanceId={}", nodeId, processInstanceId, e);
            }
        }
        execution.setVariable("step_" + nodeId + "_done", true);
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
