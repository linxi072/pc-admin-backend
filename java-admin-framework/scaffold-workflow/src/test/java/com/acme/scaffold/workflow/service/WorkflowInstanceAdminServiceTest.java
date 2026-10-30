package com.acme.scaffold.workflow.service;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.realtime.service.RealtimePushService;
import com.acme.scaffold.workflow.port.WorkflowEnginePort;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowInstanceAdminService} 离线单测：管理员实例写操作的守卫语义与引擎调用。
 *
 * <p>按项目约定，DB 交互路径（扩展表回写、审计落库）由 MySQL 集成测试覆盖；此处仅固化不触库的语义：
 * 实例不存在/已结束/状态冲突的拒绝（且<b>零引擎写</b>或<b>拒绝越权写</b>），以及合法路径确实调用引擎
 * （reassign 另验证向新办理人推送待办）。DSLContext 使用深桩，仅用于让 JooqWriters 在成功路径上静默通过。</p>
 */
class WorkflowInstanceAdminServiceTest {

    private WorkflowEnginePort engine;
    private DSLContext dsl;
    private SecurityContextFacade securityContextFacade;
    private RealtimePushService realtimePushService;
    private WorkflowInstanceAdminService service;

    @BeforeEach
    void setUp() {
        engine = mock(WorkflowEnginePort.class);
        dsl = mock(DSLContext.class, RETURNS_DEEP_STUBS);
        securityContextFacade = mock(SecurityContextFacade.class);
        realtimePushService = mock(RealtimePushService.class);
        service = new WorkflowInstanceAdminService(engine, dsl, securityContextFacade, realtimePushService);
        when(securityContextFacade.requireCurrentPrincipal())
                .thenReturn(new CurrentPrincipal(7L, "admin", "管理员", 0L, null, 1,
                        Set.of("SUPER_ADMIN"), Set.of()));
    }

    // ---- reassign ----

    @Test
    @DisplayName("reassign：目标为空 → 400，零引擎写")
    void reassign_nullTarget() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reassign("pi-1", null));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(engine, never()).setAssignee(any(), anyLong());
    }

    @Test
    @DisplayName("reassign：实例不存在 → 404，零引擎写")
    void reassign_missingInstance() {
        when(engine.status("pi-x")).thenReturn("UNKNOWN");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reassign("pi-x", 99L));
        assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(engine, never()).setAssignee(any(), anyLong());
    }

    @Test
    @DisplayName("reassign：实例已结束 → 409")
    void reassign_finishedInstance() {
        when(engine.status("pi-1")).thenReturn("FINISHED");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reassign("pi-1", 99L));
        assertEquals(CommonErrorCode.CONFLICT, ex.getErrorCode());
        verify(engine, never()).setAssignee(any(), anyLong());
    }

    @Test
    @DisplayName("reassign：无活跃任务 → 409，零引擎写")
    void reassign_noActiveTasks() {
        when(engine.status("pi-1")).thenReturn("RUNNING");
        when(engine.activeTasks("pi-1")).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reassign("pi-1", 99L));
        assertEquals(CommonErrorCode.CONFLICT, ex.getErrorCode());
        verify(engine, never()).setAssignee(any(), anyLong());
    }

    @Test
    @DisplayName("reassign：运行中且有活跃任务 → 调用引擎 setAssignee 并推送待办")
    void reassign_success() {
        when(engine.status("pi-1")).thenReturn("RUNNING");
        when(engine.activeTasks("pi-1")).thenReturn(List.of(
                new WorkflowEnginePort.TaskInfo("t-1", "e-1", "pi-1", "mgr", "经理", 5L, null)));
        service.reassign("pi-1", 99L);
        verify(engine).setAssignee("t-1", 99L);
        verify(realtimePushService).sendToUsers(any(), any(), any());
    }

    // ---- suspend ----

    @Test
    @DisplayName("suspend：实例不存在 → 404，零引擎写")
    void suspend_missing() {
        when(engine.status("pi-x")).thenReturn("UNKNOWN");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.suspend("pi-x"));
        assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(engine, never()).suspendInstance(any());
    }

    @Test
    @DisplayName("suspend：已结束 → 409，零引擎写")
    void suspend_finished() {
        when(engine.status("pi-1")).thenReturn("FINISHED");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.suspend("pi-1"));
        assertEquals(CommonErrorCode.CONFLICT, ex.getErrorCode());
        verify(engine, never()).suspendInstance(any());
    }

    @Test
    @DisplayName("suspend：已挂起 → 409，零引擎写")
    void suspend_alreadySuspended() {
        when(engine.status("pi-1")).thenReturn("RUNNING");
        when(engine.isInstanceSuspended("pi-1")).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.suspend("pi-1"));
        assertEquals(CommonErrorCode.CONFLICT, ex.getErrorCode());
        verify(engine, never()).suspendInstance(any());
    }

    @Test
    @DisplayName("suspend：运行中未挂起 → 调用引擎 suspendInstance")
    void suspend_success() {
        when(engine.status("pi-1")).thenReturn("RUNNING");
        when(engine.isInstanceSuspended("pi-1")).thenReturn(false);
        service.suspend("pi-1");
        verify(engine).suspendInstance("pi-1");
    }

    // ---- resume ----

    @Test
    @DisplayName("resume：未挂起 → 409，零引擎写")
    void resume_notSuspended() {
        when(engine.status("pi-1")).thenReturn("RUNNING");
        when(engine.isInstanceSuspended("pi-1")).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.resume("pi-1"));
        assertEquals(CommonErrorCode.CONFLICT, ex.getErrorCode());
        verify(engine, never()).resumeInstance(any());
    }

    @Test
    @DisplayName("resume：已挂起 → 调用引擎 resumeInstance")
    void resume_success() {
        when(engine.status("pi-1")).thenReturn("RUNNING");
        when(engine.isInstanceSuspended("pi-1")).thenReturn(true);
        service.resume("pi-1");
        verify(engine).resumeInstance("pi-1");
    }

    // ---- migrate ----

    @Test
    @DisplayName("migrate：实例不存在 → 404，零引擎写")
    void migrate_missing() {
        when(engine.status("pi-x")).thenReturn("UNKNOWN");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.migrate("pi-x", "pd-2"));
        assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(engine, never()).migrateInstance(any(), any());
    }

    @Test
    @DisplayName("migrate：目标与当前一致 → 400，零引擎写")
    void migrate_sameTarget() {
        when(engine.status("pi-1")).thenReturn("RUNNING");
        when(engine.processDefinitionIdOfInstance("pi-1")).thenReturn("pd-1");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.migrate("pi-1", "pd-1"));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(engine, never()).migrateInstance(any(), any());
    }

    @Test
    @DisplayName("migrate：目标定义不存在 → 404，零引擎写")
    void migrate_targetMissing() {
        when(engine.status("pi-1")).thenReturn("RUNNING");
        when(engine.processDefinitionIdOfInstance("pi-1")).thenReturn("pd-1");
        when(engine.processDefinitionState("pd-2")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.migrate("pi-1", "pd-2"));
        assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(engine, never()).migrateInstance(any(), any());
    }

    @Test
    @DisplayName("migrate：合法目标 → 调用引擎 migrateInstance")
    void migrate_success() {
        when(engine.status("pi-1")).thenReturn("RUNNING");
        when(engine.processDefinitionIdOfInstance("pi-1")).thenReturn("pd-1");
        when(engine.processDefinitionState("pd-2")).thenReturn(
                new WorkflowEnginePort.ProcessDefinitionState("pd-2", "leave", 4, false, "请假"));
        service.migrate("pi-1", "pd-2");
        verify(engine).migrateInstance("pi-1", "pd-2");
    }
}
