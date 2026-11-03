package com.acme.scaffold.workflow.service;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.monitor.domain.BusinessMetrics;
import com.acme.scaffold.realtime.service.RealtimePushService;
import com.acme.scaffold.workflow.dto.TaskView;
import com.acme.scaffold.workflow.port.WorkflowEnginePort;
import com.acme.scaffold.workflow.service.WorkflowDesignService;
import com.acme.scaffold.workflow.support.StubSecurityContextFacade;
import com.acme.scaffold.workflow.support.WfH2;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * {@link WorkflowService#myDoneTasks()} 集成测试：基于真实 H2 内存库，验证已办列表
 * 仅返回「当前用户作为原审批人且已办结」的任务，并按办结时间倒序、携带活动名快照。
 *
 * <p>引擎端口仅暴露活跃任务，已办须自查业务扩展表；本测试直接往 wf_task_ext 落测试行，
 * 覆盖：他人已办不返回、待办（PENDING）不返回、按 completed_at 倒序、活动名快照回填。</p>
 */
class WorkflowServiceMyDoneTasksTest {

    private static final long ME = 7L;
    private static final long OTHER = 5L;

    private DSLContext dsl;
    private WorkflowService service;

    @BeforeEach
    void setUp() throws Exception {
        dsl = WfH2.newContext();
        WorkflowEnginePort engine = mock(WorkflowEnginePort.class);
        BusinessMetrics businessMetrics = mock(BusinessMetrics.class);
        WorkflowDesignService designService = mock(WorkflowDesignService.class);
        RealtimePushService realtimePushService = mock(RealtimePushService.class);
        service = new WorkflowService(engine, dsl,
                new StubSecurityContextFacade(ME, "me", "我"), businessMetrics,
                designService, realtimePushService);
    }

    private void insertTaskExt(String taskId, long originalAssignee, String activityName,
                              String status, LocalDateTime completedAt) {
        JooqTables.TableRef t = JooqTables.WF_TASK_EXT;
        dsl.insertInto(t.table())
                .set(t.field("id", Long.class), System.nanoTime())
                .set(t.field("tenant_id", Long.class), 0L)
                .set(t.field("task_id", String.class), taskId)
                .set(t.field("process_instance_id", String.class), "pi-" + taskId)
                .set(t.field("activity_id", String.class), "act-" + taskId)
                .set(t.field("activity_name", String.class), activityName)
                .set(t.field("assignee_user_id", Long.class), originalAssignee)
                .set(t.field("original_assignee_user_id", Long.class), originalAssignee)
                .set(t.field("status", String.class), status)
                .set(t.field("approval_mode", String.class), "ANY")
                .set(t.field("completed_at", LocalDateTime.class), completedAt)
                .set(t.field("version", Integer.class), 0)
                .execute();
    }

    @Test
    @DisplayName("myDoneTasks：仅返回当前用户已办结任务，按办结时间倒序，含活动名快照")
    void returnsOnlyCurrentUserCompletedSortedByCompletedAt() {
        LocalDateTime now = LocalDateTime.now();
        insertTaskExt("t-me-1", ME, "经理审批", "COMPLETED", now.minusHours(2));
        insertTaskExt("t-me-2", ME, "总监审批", "COMPLETED", now); // 最近办结，应排第一
        insertTaskExt("t-other-1", OTHER, "他人审批", "COMPLETED", now.minusHours(1)); // 他人：应排除
        insertTaskExt("t-me-pending", ME, "待办节点", "PENDING", null); // 未办结：应排除

        List<TaskView> done = service.myDoneTasks();

        assertEquals(2, done.size());
        TaskView first = done.get(0);
        assertEquals("t-me-2", first.taskId());
        assertEquals("总监审批", first.name());
        assertEquals("COMPLETED", first.status());
        TaskView second = done.get(1);
        assertEquals("t-me-1", second.taskId());
        assertEquals("经理审批", second.name());
    }

    @Test
    @DisplayName("myDoneTasks：当前用户无已办 → 返回空列表")
    void returnsEmptyWhenNoCompleted() {
        insertTaskExt("t-other-1", OTHER, "他人审批", "COMPLETED", LocalDateTime.now());

        List<TaskView> done = service.myDoneTasks();

        assertEquals(0, done.size());
    }
}
