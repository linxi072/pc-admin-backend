package com.acme.scaffold.workflow.adapter.step;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 自定义工作流「执行步骤(SERVICE)」节点委托：流程流转到该节点时自动执行（如调用外部系统、写业务数据等），
 * 执行完成后自动推进到下一节点。当前为占位实现，记录节点与业务单据并预留扩展点。
 */
@Slf4j
@Component
public class GenericStepDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String nodeId = execution.getCurrentActivityId();
        String businessKey = execution.getProcessInstanceId();
        log.info("[工作流执行步骤] 节点={} processInstanceId={}", nodeId, businessKey);
        // 扩展点：在此调用外部服务 / 落库步骤执行记录，本迭代仅做结构化日志与变量标记。
        execution.setVariable("step_" + nodeId + "_done", true);
    }
}
