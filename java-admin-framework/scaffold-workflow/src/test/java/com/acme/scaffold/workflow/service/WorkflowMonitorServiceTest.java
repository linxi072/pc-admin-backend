package com.acme.scaffold.workflow.service;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.realtime.service.RealtimePushService;
import com.acme.scaffold.workflow.config.WorkflowMonitorProperties;
import com.acme.scaffold.workflow.dto.DefinitionVersionView;
import com.acme.scaffold.workflow.port.WorkflowEnginePort;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowMonitorService} 离线单测：催办写路径的守卫语义与只读投影映射。
 *
 * <p>DB 交互路径（运行实例分页/超时任务回填/催办记录落库）按项目约定由 MySQL 集成测试覆盖
 * ——mock jOOQ 链式 API 既脆弱又偏离真实实现；此处仅固化不触库的语义：任务不存在/无办理人
 * 的拒绝（且<b>零数据库与零推送交互</b>）、版本链映射、无超时任务的空结果短路。</p>
 */
class WorkflowMonitorServiceTest {

    private WorkflowEnginePort engine;
    private DSLContext dsl;
    private SecurityContextFacade securityContextFacade;
    private WorkflowMonitorProperties properties;
    private RealtimePushService realtimePushService;
    private WorkflowMonitorService service;

    @BeforeEach
    void setUp() {
        engine = mock(WorkflowEnginePort.class);
        dsl = mock(DSLContext.class);
        securityContextFacade = mock(SecurityContextFacade.class);
        properties = new WorkflowMonitorProperties();
        realtimePushService = mock(RealtimePushService.class);
        service = new WorkflowMonitorService(engine, dsl, securityContextFacade, properties,
                realtimePushService);
        when(securityContextFacade.requireCurrentPrincipal())
                .thenReturn(new CurrentPrincipal(7L, "admin", "管理员", 0L, null, 1,
                        Set.of("SUPER_ADMIN"), Set.of()));
    }

    @Test
    @DisplayName("remind：任务不存在/已完成 → 404，零 DB 零推送")
    void remind_rejectsMissingTask() {
        when(engine.getTask("t-404")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.remind("t-404"));
        assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(dsl);
        verifyNoInteractions(realtimePushService);
    }

    @Test
    @DisplayName("remind：池化任务无办理人 → 409（先认领再催办），零 DB 零推送")
    void remind_rejectsUnassignedTask() {
        when(engine.getTask("t-1")).thenReturn(new WorkflowEnginePort.TaskInfo(
                "t-1", "e-1", "pi-1", "manager", "部门经理", null, null));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.remind("t-1"));
        assertEquals(CommonErrorCode.CONFLICT, ex.getErrorCode());
        verifyNoInteractions(dsl);
        verifyNoInteractions(realtimePushService);
    }

    @Test
    @DisplayName("definitionVersions：引擎版本链按字段透传映射（含挂起态与部署时间）")
    void definitionVersions_mapsEngineRecords() {
        LocalDateTime deployTime = LocalDateTime.of(2026, 10, 20, 9, 30);
        when(engine.definitionVersions("leaveApproval")).thenReturn(List.of(
                new WorkflowEnginePort.DefinitionVersion("leaveApproval:2:3", 3, "请假审批",
                        "leaveApproval", true, "d-2", deployTime),
                new WorkflowEnginePort.DefinitionVersion("leaveApproval:1:2", 1, "请假审批",
                        "leaveApproval", false, "d-1", null)));
        List<DefinitionVersionView> views = service.definitionVersions("leaveApproval");
        assertEquals(2, views.size());
        DefinitionVersionView latest = views.get(0);
        assertEquals(3, latest.version());
        assertEquals("leaveApproval:2:3", latest.id());
        assertEquals("请假审批", latest.name());
        assertEquals("leaveApproval", latest.key());
        assertTrue(latest.suspended());
        assertEquals("d-2", latest.deploymentId());
        assertEquals(deployTime, latest.deployTime());
        assertEquals(1, views.get(1).version());
    }

    @Test
    @DisplayName("overdueTasks：无超时任务时短路返回空页，不回查实例与用户名")
    void overdueTasks_emptyShortCircuit() {
        when(engine.countOverdueTasks(any(LocalDateTime.class))).thenReturn(0L);
        var result = service.overdueTasks(1, 10);
        assertEquals(0L, result.total());
        assertTrue(result.records().isEmpty());
        verifyNoInteractions(dsl);
    }

    // ------------------------------------------------------------------
    // 定义生命周期：挂起/激活
    // ------------------------------------------------------------------

    @Test
    @DisplayName("suspendDefinition：定义不存在 → 404，零引擎写")
    void suspendDefinition_rejectsMissing() {
        when(engine.processDefinitionState("pd-x")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.suspendDefinition("pd-x"));
        assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(engine, never()).suspendDefinition(any());
    }

    @Test
    @DisplayName("suspendDefinition：已挂起 → 409，零引擎写")
    void suspendDefinition_rejectsAlreadySuspended() {
        when(engine.processDefinitionState("pd-1")).thenReturn(
                new WorkflowEnginePort.ProcessDefinitionState("pd-1", "leave", 3, true, "请假"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.suspendDefinition("pd-1"));
        assertEquals(CommonErrorCode.CONFLICT, ex.getErrorCode());
        verify(engine, never()).suspendDefinition(any());
    }

    @Test
    @DisplayName("suspendDefinition：激活态 → 调用引擎 suspendDefinition")
    void suspendDefinition_callsEngineWhenActive() {
        when(engine.processDefinitionState("pd-1")).thenReturn(
                new WorkflowEnginePort.ProcessDefinitionState("pd-1", "leave", 3, false, "请假"));
        service.suspendDefinition("pd-1");
        verify(engine).suspendDefinition("pd-1");
    }

    @Test
    @DisplayName("activateDefinition：已激活 → 409，零引擎写")
    void activateDefinition_rejectsAlreadyActive() {
        when(engine.processDefinitionState("pd-1")).thenReturn(
                new WorkflowEnginePort.ProcessDefinitionState("pd-1", "leave", 3, false, "请假"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.activateDefinition("pd-1"));
        assertEquals(CommonErrorCode.CONFLICT, ex.getErrorCode());
        verify(engine, never()).activateDefinition(any());
    }

    @Test
    @DisplayName("activateDefinition：挂起态 → 调用引擎 activateDefinition")
    void activateDefinition_callsEngineWhenSuspended() {
        when(engine.processDefinitionState("pd-1")).thenReturn(
                new WorkflowEnginePort.ProcessDefinitionState("pd-1", "leave", 3, true, "请假"));
        service.activateDefinition("pd-1");
        verify(engine).activateDefinition("pd-1");
    }
}
