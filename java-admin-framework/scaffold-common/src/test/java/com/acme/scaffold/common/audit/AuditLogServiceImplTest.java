package com.acme.scaffold.common.audit;

import com.acme.scaffold.common.api.dto.KeysetPageResult;
import com.acme.scaffold.common.audit.domain.SysOperationLogDO;
import com.acme.scaffold.common.audit.dto.AuditLogQuery;
import com.acme.scaffold.jooq.paging.JooqKeysetPager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * {@link AuditLogServiceImpl} 落库时机单测：不连数据库，用「只记录不执行」的 Executor 观察投递时机。
 *
 * <p>锁定一条关键约束：审计必须在<b>业务事务提交之后</b>才落库。
 * 历史上本方法标注 {@code @Async}，而事务同步是线程绑定的，切线程后
 * {@code TransactionSynchronizationManager} 判定恒为「无事务」，审计会在业务提交前写入，
 * 业务一旦回滚，审计里就留下一条「假成功」。</p>
 */
class AuditLogServiceImplTest {

    /** 记录被投递的插入任务但不真正执行，避免依赖数据库。 */
    private final List<Runnable> submitted = new ArrayList<>();

    private final Executor recordingExecutor = submitted::add;

    private final AuditLogServiceImpl service =
            new AuditLogServiceImpl(Mockito.mock(DSLContext.class), recordingExecutor, new ObjectMapper());

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("事务中：提交前不落库，afterCommit 后才投递插入任务")
    void defersInsertUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();
        service.saveAsync(AuditRecord.of("system", "UPDATE", "编辑用户"));

        assertTrue(submitted.isEmpty(), "事务未提交前不得投递插入任务，否则业务回滚会留下假成功记录");

        List<TransactionSynchronization> syncs =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        assertEquals(1, syncs.size(), "应注册且仅注册一个事务同步回调");

        syncs.get(0).afterCommit();
        assertEquals(1, submitted.size(), "事务提交后立即投递一次插入任务");
    }

    @Test
    @DisplayName("无事务上下文：立即投递插入任务")
    void submitsImmediatelyWithoutTransaction() {
        service.saveAsync(AuditRecord.of("system", "QUERY", "查询用户"));
        assertEquals(1, submitted.size());
    }

    @Test
    @DisplayName("线程池拒绝时退化为同步写入，且不向调用方抛出异常")
    void fallsBackToSynchronousWriteWhenExecutorRejects() {
        AuditLogServiceImpl rejecting = new AuditLogServiceImpl(Mockito.mock(DSLContext.class),
                task -> {
                    throw new RejectedExecutionException("queue full");
                }, new ObjectMapper());

        // 审计属合规留痕：线程池满不能让整条记录丢失，也不能把异常抛回业务链路
        assertDoesNotThrow(() -> rejecting.saveAsync(AuditRecord.of("system", "DELETE", "删除用户")),
                "线程池拒绝时应在内部降级为同步写入，不影响业务调用方");
    }

    // ------------------------------------------------------------------
    // keyset 深翻页
    // ------------------------------------------------------------------

    @Test
    @DisplayName("keysetPage：复用筛选条件并映射视图，nextCursor 透传，脱敏随 sensitiveVisible 切换")
    void keysetPage_mapsViewsAndPropagatesCursor() {
        SysOperationLogDO d1 = new SysOperationLogDO();
        d1.setId(11L);
        d1.setIpAddress("203.0.113.45");
        // 摘要含可识别 PII（手机号），未授权时 maskSummary 会改写，授权时保留原文——便于断言脱敏开关
        d1.setRequestSummary("call 13800138000 at night");
        d1.setOccurredAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        SysOperationLogDO d2 = new SysOperationLogDO();
        d2.setId(12L);
        d2.setIpAddress("198.51.100.7");
        d2.setRequestSummary("call 13900139000 at noon");
        d2.setOccurredAt(java.time.LocalDateTime.of(2026, 1, 2, 0, 0));

        // 全量筛选（所有过滤项为空）——仅验证 keyset 编排不依赖 dsl，由 JooqKeysetPager 单测覆盖 seek/limit
        AuditLogQuery query = new AuditLogQuery(1, 20, null, null, null, null,
                null, null, null, null, null, null, null, null);

        try (MockedStatic<JooqKeysetPager> mw = mockStatic(JooqKeysetPager.class)) {
            mw.when(() -> JooqKeysetPager.<SysOperationLogDO>query(
                            any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(KeysetPageResult.of(20, List.of(d1, d2), "NEXT_CURSOR"));

            KeysetPageResult<AuditLogView> masked = service.keysetPage(query, "", false);
            assertEquals(2, masked.records().size());
            assertEquals(20, masked.size());
            assertEquals("NEXT_CURSOR", masked.nextCursor());
            // 脱敏：未授权时摘要（含手机号）被 maskSummary 改写、IP 始终掩码
            assertNotEquals("call 13800138000 at night", masked.records().get(0).requestSummary());
            assertNotEquals("203.0.113.45", masked.records().get(0).ipAddress());

            KeysetPageResult<AuditLogView> raw = service.keysetPage(query, "NEXT_CURSOR", true);
            // 授权时摘要保留原文，IP 仍掩码（个人轨迹不暴露）
            assertEquals("call 13800138000 at night", raw.records().get(0).requestSummary());
            assertNotEquals("203.0.113.45", raw.records().get(0).ipAddress());
        }
    }
}
