package com.acme.scaffold.job.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.job.domain.JobDagRules;
import com.acme.scaffold.job.domain.JobStatus;
import com.acme.scaffold.job.domain.JobTriggerType;
import com.acme.scaffold.job.entity.SysJobDO;
import com.acme.scaffold.job.port.JobDagPort;
import com.acme.scaffold.job.port.JobSchedulerPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobDagService} 单元测试：以 Mockito 模拟 {@link JobDagPort} 与 {@link JobSchedulerPort}，
 * 验证「上游成功后驱动就绪下游」「手动触发下游」「删除守卫」「依赖就绪判定」「编辑环检测」等核心编排逻辑。
 *
 * <p>触发调度（{@code scheduleCron/Interval/OneTime}）与状态写回（{@code setRunning}）均为 Mock 验证点，
 * 不依赖真实 JobRunr / 数据库。
 */
@ExtendWith(MockitoExtension.class)
class JobDagServiceTest {

    @Mock
    private JobDagPort dagPort;
    @Mock
    private JobSchedulerPort schedulerPort;

    private JobDagService service;

    @BeforeEach
    void setUp() {
        service = new JobDagService(dagPort, schedulerPort);
    }

    private SysJobDO job(Long id, String status, String triggerType, String dependsOn, Long successRuns) {
        SysJobDO j = new SysJobDO();
        j.setId(id);
        j.setJobName("job-" + id);
        j.setStatus(status);
        j.setTriggerType(triggerType);
        j.setCronExpression("*/5 * * * *");
        j.setDependsOn(dependsOn);
        j.setSuccessRuns(successRuns);
        return j;
    }

    // ------------------------------------------------------------------
    // onJobSuccess
    // ------------------------------------------------------------------

    @Test
    void onJobSuccessTriggersReadyWaitingDependent() {
        SysJobDO upstream = job(10L, JobStatus.COMPLETED.name(), "ONE_TIME", null, 1L);
        SysJobDO dependent = job(20L, JobStatus.WAITING.name(), "CRON", "[10]", 0L);

        when(dagPort.findDependents(10L)).thenReturn(List.of(dependent));
        when(dagPort.findById(10L)).thenReturn(Optional.of(upstream));
        when(schedulerPort.available()).thenReturn(true);
        when(schedulerPort.scheduleCron(20L, "*/5 * * * *")).thenReturn("ref-20");

        service.onJobSuccess(10L);

        verify(schedulerPort, times(1)).scheduleCron(20L, "*/5 * * * *");
        verify(dagPort, times(1)).setRunning(eq(20L), eq("ref-20"), any());
    }

    @Test
    void onJobSuccessDoesNotTriggerRunningDependent() {
        // 下游为 RUNNING：onJobSuccess 在 WAITING 门控处即过滤掉，不会调用 findById/isReady
        SysJobDO dependent = job(20L, JobStatus.RUNNING.name(), "CRON", "[10]", 0L);

        when(dagPort.findDependents(10L)).thenReturn(List.of(dependent));

        service.onJobSuccess(10L);

        verify(schedulerPort, never()).scheduleCron(anyLong(), any());
        verify(dagPort, never()).setRunning(anyLong(), any(), any());
    }

    @Test
    void onJobSuccessDoesNotTriggerUnreadyDependent() {
        // 上游 success_runs=0 → 依赖未满足 → 不应触发
        SysJobDO upstream = job(10L, JobStatus.RUNNING.name(), "CRON", null, 0L);
        SysJobDO dependent = job(20L, JobStatus.WAITING.name(), "CRON", "[10]", 0L);

        when(dagPort.findDependents(10L)).thenReturn(List.of(dependent));
        when(dagPort.findById(10L)).thenReturn(Optional.of(upstream));

        service.onJobSuccess(10L);

        verify(schedulerPort, never()).scheduleCron(anyLong(), any());
        verify(dagPort, never()).setRunning(anyLong(), any(), any());
    }

    @Test
    void onJobSuccessNoDependentsIsNoOp() {
        when(dagPort.findDependents(10L)).thenReturn(List.of());
        service.onJobSuccess(10L);
        verify(schedulerPort, never()).scheduleCron(anyLong(), any());
        verify(dagPort, never()).setRunning(anyLong(), any(), any());
    }

    // ------------------------------------------------------------------
    // triggerDownstream
    // ------------------------------------------------------------------

    @Test
    void triggerDownstreamCountsOnlyReadyWaiting() {
        SysJobDO upstream = job(10L, JobStatus.COMPLETED.name(), "ONE_TIME", null, 1L);
        SysJobDO waiting = job(20L, JobStatus.WAITING.name(), "CRON", "[10]", 0L);
        SysJobDO running = job(21L, JobStatus.RUNNING.name(), "CRON", "[10]", 0L);

        when(dagPort.findDependents(10L)).thenReturn(List.of(waiting, running));
        when(dagPort.findById(10L)).thenReturn(Optional.of(upstream));
        when(schedulerPort.available()).thenReturn(true);
        when(schedulerPort.scheduleCron(anyLong(), any())).thenReturn("ref");

        int count = service.triggerDownstream(10L);

        assertEquals(1, count);
        verify(schedulerPort, times(1)).scheduleCron(eq(20L), any());
        verify(schedulerPort, never()).scheduleCron(eq(21L), any());
    }

    // ------------------------------------------------------------------
    // assertNoDependents
    // ------------------------------------------------------------------

    @Test
    void assertNoDependentsThrowsWhenReferenced() {
        when(dagPort.dependentIds(10L)).thenReturn(List.of(20L));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.assertNoDependents(10L));
        assertTrue(ex.getMessage().contains("依赖"));
    }

    @Test
    void assertNoDependentsPassesWhenOrphan() {
        when(dagPort.dependentIds(10L)).thenReturn(List.of());
        service.assertNoDependents(10L); // 不抛
    }

    // ------------------------------------------------------------------
    // isReady
    // ------------------------------------------------------------------

    @Test
    void isReadyTrueWhenNoDeps() {
        SysJobDO job = job(20L, JobStatus.PAUSED.name(), "CRON", null, 0L);
        when(dagPort.findById(20L)).thenReturn(Optional.of(job));
        assertTrue(service.isReady(20L));
    }

    @Test
    void isReadyTrueWhenDepCompleted() {
        SysJobDO dep = job(10L, JobStatus.COMPLETED.name(), "ONE_TIME", null, 1L);
        SysJobDO job = job(20L, JobStatus.PAUSED.name(), "CRON", "[10]", 0L);
        when(dagPort.findById(20L)).thenReturn(Optional.of(job));
        when(dagPort.findById(10L)).thenReturn(Optional.of(dep));
        assertTrue(service.isReady(20L));
    }

    @Test
    void isReadyFalseWhenDepNotSucceeded() {
        SysJobDO dep = job(10L, JobStatus.PAUSED.name(), "CRON", null, 0L);
        SysJobDO job = job(20L, JobStatus.PAUSED.name(), "CRON", "[10]", 0L);
        when(dagPort.findById(20L)).thenReturn(Optional.of(job));
        when(dagPort.findById(10L)).thenReturn(Optional.of(dep));
        assertFalse(service.isReady(20L));
    }

    @Test
    void isReadyTrueWhenDepDeleted() {
        // 上游已删除（视为满足，避免悬空边永久阻塞）
        SysJobDO job = job(20L, JobStatus.PAUSED.name(), "CRON", "[10]", 0L);
        when(dagPort.findById(20L)).thenReturn(Optional.of(job));
        when(dagPort.findById(10L)).thenReturn(Optional.empty());
        assertTrue(service.isReady(20L));
    }

    // ------------------------------------------------------------------
    // validateDependencyEdit
    // ------------------------------------------------------------------

    @Test
    void validateDependencyEditRejectsNonExistent() {
        when(dagPort.allIds()).thenReturn(Set.of(1L, 2L));
        assertThrows(BusinessException.class,
                () -> service.validateDependencyEdit(10L, List.of(99L)));
    }

    @Test
    void validateDependencyEditRejectsSelf() {
        when(dagPort.allIds()).thenReturn(Set.of(10L));
        assertThrows(BusinessException.class,
                () -> service.validateDependencyEdit(10L, List.of(10L)));
    }

    @Test
    void validateDependencyEditRejectsCycle() {
        // 20 -> 10 -> 20 形成环：环检测仅需读取被依赖方（10）的 depends_on，selfId(20) 的依赖来自入参
        when(dagPort.allIds()).thenReturn(Set.of(10L, 20L));
        when(dagPort.findById(10L)).thenReturn(Optional.of(job(10L, "PAUSED", "CRON", "[20]", 0L)));
        assertThrows(BusinessException.class,
                () -> service.validateDependencyEdit(20L, List.of(10L)));
    }

    @Test
    void validateDependencyEditAcceptsAcyclic() {
        when(dagPort.allIds()).thenReturn(Set.of(10L, 20L, 30L));
        when(dagPort.findById(20L)).thenReturn(Optional.of(job(20L, "PAUSED", "CRON", "[30]", 0L)));
        when(dagPort.findById(30L)).thenReturn(Optional.of(job(30L, "PAUSED", "CRON", null, 0L)));
        // 10 依赖 20（20 依赖 30，无环）→ 通过
        service.validateDependencyEdit(10L, List.of(20L));
    }

    // ------------------------------------------------------------------
    // dependentsMap
    // ------------------------------------------------------------------

    @Test
    void dependentsMapAggregates() {
        SysJobDO a = job(20L, "PAUSED", "CRON", "[10]", 0L);
        SysJobDO b = job(21L, "PAUSED", "CRON", "[10]", 0L);
        when(dagPort.dependentsMap(Set.of(10L))).thenReturn(Map.of(10L, List.of(20L, 21L)));
        assertEquals(List.of(20L, 21L), service.dependentsMap(Set.of(10L)).get(10L));
    }

    // 引用一下 JobDagRules，确保序列化工具可用（纯函数层冒烟）
    @Test
    void serializeSmoke() {
        assertEquals("[1,2]", JobDagRules.serializeDependsOn(List.of(1L, 2L)));
    }
}
