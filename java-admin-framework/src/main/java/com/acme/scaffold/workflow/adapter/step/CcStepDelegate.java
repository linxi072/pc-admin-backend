package com.acme.scaffold.workflow.adapter.step;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 自定义工作流「抄送(CC)」节点委托：流程流转到该节点时发送通知（占位为结构化日志），不阻塞流程，
 * 执行完成后自动推进到下一节点。
 */
@Slf4j
@Component
public class CcStepDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String nodeId = execution.getCurrentActivityId();
        String businessKey = execution.getProcessInstanceId();
        log.info("[工作流抄送] 节点={} processInstanceId={}", nodeId, businessKey);
        execution.setVariable("cc_" + nodeId + "_done", true);
    }
}
