package com.acme.scaffold.workflow.service;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.monitor.domain.BusinessMetrics;
import com.acme.scaffold.realtime.service.RealtimePushService;
import com.acme.scaffold.workflow.dto.DelegateTaskRequest;
import com.acme.scaffold.workflow.dto.ResolveDelegateRequest;
import com.acme.scaffold.workflow.port.WorkflowEnginePort;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowService} 委派能力离线单测：委派（delegate）与办结交还（resolveDelegate）的守卫语义与引擎调用。
 *
 * <p>沿用模块既有约定——离线单测只固化不触库语义（任务不存在/非本人/状态冲突的拒绝且<b>零委派写</b>），
 * DB 回写由集成测试覆盖；DSLContext 使用深桩让 JooqWriters 在成功路径静默通过。
 * {@code @Idempotent} 为 Spring AOP 切面，纯单测不加载上下文，故直接以 {@code existsOperation} 内部判定覆盖。</p>
 */
class WorkflowServiceDelegateTest {

    private WorkflowEnginePort engine;
    private DSLContext dsl;
    private SecurityContextFacade securityContextFacade;
    private WorkflowService service;

    private static final long ME = 7L;
    private static final long OTHER = 5L;
    private static final long DELEGATEE = 99L;

    @BeforeEach
    void setUp() {
        engine = mock(WorkflowEnginePort.class);
        dsl = mock(DSLContext.class, RETURNS_DEEP_STUBS);
        securityContextFacade = mock(SecurityContextFacade.class);
        BusinessMetrics businessMetrics = mock(BusinessMetrics.class);
        WorkflowDesignService designService = mock(WorkflowDesignService.class);
        RealtimePushService realtimePushService = mock(RealtimePushService.class);
        service = new WorkflowService(engine, dsl, securityContextFacade, businessMetrics,
                designService, realtimePushService);
        when(securityContextFacade.requireCurrentPrincipal())
                .thenReturn(new CurrentPrincipal(ME, "me", "我", 0L, null, 1,
                        Set.of("SUPER_ADMIN"), Set.of()));
    }

    private WorkflowEnginePort.TaskInfo task(long assignee, String delegationState) {
        return new WorkflowEnginePort.TaskInfo("t-1", "e-1", "pi-1", "mgr", "经理",
                assignee == 0 ? null : assignee, null, delegationState);
    }

    /** 将当前操作用户切换为指定 userId（委派相关用例中以被委托人身份发起）。 */
    private void asUser(long userId) {
        when(securityContextFacade.requireCurrentPrincipal())
                .thenReturn(new CurrentPrincipal(userId, "u" + userId, "用户" + userId, 0L, null, 1,
                        Set.of("SUPER_ADMIN"), Set.of()));
    }

    // ---------------- delegate ----------------

    @Test
    @DisplayName("delegate：任务不存在 → 404，零委派写")
    void delegate_missingTask() {
        when(engine.getTask("t-1")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delegate(new DelegateTaskRequest("t-1", DELEGATEE, "出差", "op-1")));
        assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(engine, never()).delegate(any(), anyLong());
    }

    @Test
    @DisplayName("delegate：非本人待办 → 403，零委派写")
    void delegate_notMine() {
        when(engine.getTask("t-1")).thenReturn(task(OTHER, null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delegate(new DelegateTaskRequest("t-1", DELEGATEE, "出差", "op-1")));
        assertEquals(CommonErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(engine, never()).delegate(any(), anyLong());
    }

    @Test
    @DisplayName("delegate：已处于委派态 → 409，零重复委派")
    void delegate_alreadyPending() {
        when(engine.getTask("t-1")).thenReturn(task(ME, "PENDING"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delegate(new DelegateTaskRequest("t-1", DELEGATEE, "出差", "op-1")));
        assertEquals(CommonErrorCode.CONFLICT, ex.getErrorCode());
        verify(engine, never()).delegate(any(), anyLong());
    }

    @Test
    @DisplayName("delegate：本人待办 → 调用引擎 delegate 并交托给被委托人")
    void delegate_success() {
        when(engine.getTask("t-1")).thenReturn(task(ME, null));
        service.delegate(new DelegateTaskRequest("t-1", DELEGATEE, "出差代办", "op-1"));
        verify(engine).delegate(eq("t-1"), eq(DELEGATEE));
    }

    // ---------------- resolveDelegate ----------------

    @Test
    @DisplayName("resolveDelegate：任务不存在 → 404，零交还写")
    void resolve_missingTask() {
        when(engine.getTask("t-1")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resolveDelegate(new ResolveDelegateRequest("t-1", "已办", "op-2")));
        assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(engine, never()).resolve(any());
    }

    @Test
    @DisplayName("resolveDelegate：当前用户非被委托人 → 403，零交还写")
    void resolve_notDelegatee() {
        when(engine.getTask("t-1")).thenReturn(task(OTHER, "PENDING"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resolveDelegate(new ResolveDelegateRequest("t-1", "已办", "op-2")));
        assertEquals(CommonErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(engine, never()).resolve(any());
    }

    @Test
    @DisplayName("resolveDelegate：非委派待办态 → 409，零交还写")
    void resolve_notPending() {
        asUser(DELEGATEE);
        when(engine.getTask("t-1")).thenReturn(task(DELEGATEE, null));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resolveDelegate(new ResolveDelegateRequest("t-1", "已办", "op-2")));
        assertEquals(CommonErrorCode.CONFLICT, ex.getErrorCode());
        verify(engine, never()).resolve(any());
    }

    @Test
    @DisplayName("resolveDelegate：被委托人办结 → 调用引擎 resolve 交还原审批人")
    void resolve_success() {
        asUser(DELEGATEE);
        when(engine.getTask("t-1")).thenReturn(task(DELEGATEE, "PENDING"));
        service.resolveDelegate(new ResolveDelegateRequest("t-1", "已办妥", "op-2"));
        verify(engine).resolve(eq("t-1"));
    }
}
