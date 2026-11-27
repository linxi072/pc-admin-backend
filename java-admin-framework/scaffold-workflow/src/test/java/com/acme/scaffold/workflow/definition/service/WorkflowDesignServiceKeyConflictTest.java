package com.acme.scaffold.workflow.definition.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.workflow.definition.adapter.AssigneeResolver;
import com.acme.scaffold.workflow.definition.adapter.BpmnWorkflowBuilder;
import com.acme.scaffold.workflow.definition.domain.model.WfWorkflowDesign;
import com.acme.scaffold.workflow.definition.dto.design.CreateDesignRequest;
import com.acme.scaffold.workflow.engine.domain.repository.WorkflowEnginePort;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;

/** 流程编码唯一键并发兜底回归：create 为幂等语义，冲突时须返回既有设计；无既有行才显式 409。 */
class WorkflowDesignServiceKeyConflictTest {

    private static WorkflowDesignService service() {
        return new WorkflowDesignService(mock(WorkflowEnginePort.class), mock(DSLContext.class),
                mock(AssigneeResolver.class), mock(SecurityContextFacade.class));
    }

    private static WfWorkflowDesign winner() {
        WfWorkflowDesign d = new WfWorkflowDesign();
        d.setId(42L);
        d.setProcessKey("LEAVE_APPROVAL");
        d.setProcessName("请假审批");
        d.setStatus("DRAFT");
        d.setVersion(0);
        d.setNodesJson("[]");
        d.setEdgesJson("[]");
        return d;
    }

    /** mock 环境下 notDeleted 须返回真实条件，否则 notDeleted(...).and(...) 链式 NPE。 */
    private static void stubNotDeleted(MockedStatic<SqlWriters> writers) {
        writers.when(() -> SqlWriters.notDeleted(any(SqlTables.TableRef.class)))
                .thenReturn(DSL.trueCondition());
    }

    @Test
    void concurrentCreateOnConflictShouldReturnExistingDesign() {
        WorkflowDesignService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            stubNotDeleted(writers);
            // 第一次 fetchOne（查重）为空；insert 冲突后重查返回既有行；get(id) 经 fetchById 返回同一行
            writers.when(() -> SqlWriters.fetchOne(any(DSLContext.class), eq(SqlTables.WF_WORKFLOW_DESIGN),
                    eq(WfWorkflowDesign.class), any())).thenReturn(null, winner());
            writers.when(() -> SqlWriters.insert(any(DSLContext.class), eq(SqlTables.WF_WORKFLOW_DESIGN),
                    any(WfWorkflowDesign.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.WF_WORKFLOW_DESIGN),
                    eq(WfWorkflowDesign.class), eq(42L))).thenReturn(winner());

            var view = service.create(new CreateDesignRequest("LEAVE_APPROVAL", "请假审批", null));

            assertEquals(42L, view.id());
            assertEquals("LEAVE_APPROVAL", view.processKey());
        }
    }

    @Test
    void conflictWithoutWinnerShouldReturnExplicitConflict() {
        WorkflowDesignService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            stubNotDeleted(writers);
            writers.when(() -> SqlWriters.fetchOne(any(DSLContext.class), eq(SqlTables.WF_WORKFLOW_DESIGN),
                    eq(WfWorkflowDesign.class), any())).thenReturn(null);
            writers.when(() -> SqlWriters.insert(any(DSLContext.class), eq(SqlTables.WF_WORKFLOW_DESIGN),
                    any(WfWorkflowDesign.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.create(new CreateDesignRequest("LEAVE_APPROVAL", "请假审批", null)));

            assertEquals(CommonErrorCode.CONFLICT, ex.getErrorCode());
            assertEquals("流程编码已存在", ex.getMessage());
        }
    }

    @Test
    void normalCreateShouldNotBeSwallowedByConflictGuard() {
        WorkflowDesignService service = service();
        WfWorkflowDesign created = winner();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            stubNotDeleted(writers);
            writers.when(() -> SqlWriters.fetchOne(any(DSLContext.class), eq(SqlTables.WF_WORKFLOW_DESIGN),
                    eq(WfWorkflowDesign.class), any())).thenReturn(null);
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.WF_WORKFLOW_DESIGN),
                    eq(WfWorkflowDesign.class), any())).thenReturn(created);

            var view = assertDoesNotThrow(() ->
                    service.create(new CreateDesignRequest("LEAVE_APPROVAL", "请假审批", null)));

            assertEquals("LEAVE_APPROVAL", view.processKey());
        }
    }
}
