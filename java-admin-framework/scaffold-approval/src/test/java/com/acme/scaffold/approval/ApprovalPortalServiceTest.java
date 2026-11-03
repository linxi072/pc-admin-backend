package com.acme.scaffold.approval;

import com.acme.scaffold.workflow.dto.InstanceDetailView;
import com.acme.scaffold.workflow.dto.InstanceView;
import com.acme.scaffold.workflow.dto.TaskView;
import com.acme.scaffold.workflow.dto.design.WorkflowDesignView;
import com.acme.scaffold.workflow.service.WorkflowDesignService;
import com.acme.scaffold.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalPortalService} 编排单测：门户为薄编排层，以 Mockito 桩验证
 * 待办/已办/我发起/可发起（PUBLISHED 过滤）/详情聚合与统计正确，不触库。
 */
class ApprovalPortalServiceTest {

    private WorkflowService workflowService;
    private WorkflowDesignService designService;
    private ApprovalPortalService portalService;

    @BeforeEach
    void setUp() {
        workflowService = mock(WorkflowService.class);
        designService = mock(WorkflowDesignService.class);
        portalService = new ApprovalPortalService(workflowService, designService);
    }

    private TaskView task(String id, String status) {
        return new TaskView(id, "pi-" + id, "act", "节点", status, 1L, null, null, null);
    }

    private WorkflowDesignView design(String key, String status) {
        return new WorkflowDesignView(null, key, "流程" + key, null, status, 1,
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("dashboard：聚合待办/已办/我发起/可发起与统计")
    void dashboard_aggregates() {
        when(workflowService.myTasks()).thenReturn(List.of(task("t1", "PENDING")));
        when(workflowService.myDoneTasks()).thenReturn(List.of(task("d1", "COMPLETED")));
        when(workflowService.myInstances())
                .thenReturn(List.of(new InstanceView("pi1", "leave", "1", "请假", "RUNNING", null, null)));
        when(designService.list()).thenReturn(List.of(
                design("leave", "PUBLISHED"),
                design("expense", "PUBLISHED"),
                design("draft", "DRAFT")));

        ApprovalPortalView view = portalService.dashboard();

        assertEquals(1, view.todoTasks().size());
        assertEquals(1, view.doneTasks().size());
        assertEquals(1, view.myInstances().size());
        assertEquals(2, view.startable().size()); // DRAFT 被过滤
        assertEquals(1, view.stats().todoCount());
        assertEquals(1, view.stats().doneCount());
        assertEquals(1, view.stats().instanceCount());
        assertEquals(2, view.stats().startableCount());
    }

    @Test
    @DisplayName("startable：仅返回 PUBLISHED 设计")
    void startable_filtersPublished() {
        when(designService.list()).thenReturn(List.of(
                design("a", "PUBLISHED"),
                design("b", "DRAFT"),
                design("c", "DEPRECATED")));

        List<WorkflowDesignView> startable = portalService.startable();

        assertEquals(1, startable.size());
        assertEquals("a", startable.get(0).processKey());
    }

    @Test
    @DisplayName("detail：透传工作流详情")
    void detail_delegates() {
        InstanceDetailView detail = mock(InstanceDetailView.class);
        when(workflowService.detail("pi-x")).thenReturn(detail);

        assertSame(detail, portalService.detail("pi-x"));
    }
}
