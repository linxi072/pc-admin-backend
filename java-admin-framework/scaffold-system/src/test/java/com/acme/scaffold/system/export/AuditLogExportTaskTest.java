package com.acme.scaffold.system.export;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.AuditLogService;
import com.acme.scaffold.common.audit.AuditLogView;
import com.acme.scaffold.common.sensitive.SensitiveMasker;
import com.acme.scaffold.system.maskstrategy.MaskStrategyResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuditLogExportTask} 单元测试（M20 增强 + M21 解析器接入）：
 * 验证「导出中心模板注册」契约与脱敏。
 *
 * <p>重点锁定：表头与行宽一致（否则 CSV/XLSX 会错列）、以脱敏视图（sensitiveVisible=false）取数、
 * IP 经 {@link MaskStrategyResolver} 兜底掩码（数据离开系统边界不应暴露原文）。
 * 未命中策略时解析器回落默认掩码（与 M20 既有 maskIp 口径一致），故本测试对解析器做等价打桩，
 * 既覆盖接入点又不依赖脱敏策略表。</p>
 */
class AuditLogExportTaskTest {

    private static AuditLogView sampleView() {
        return new AuditLogView(2001L, "audit", "READ", "查询审计日志列表", 7L, "admin", "GET",
                "/api/system/audit-logs/page", "{\"keyword\":\"x\"}", null, "0", 12L,
                "192.168.1.100", "trace-xyz", true, LocalDateTime.of(2026, 10, 16, 9, 30));
    }

    /** 构建导出任务：解析器按「未命中策略→默认 maskIp」等价打桩，覆盖 M21 接入点。 */
    private static AuditLogExportTask taskWith(AuditLogService svc) {
        MaskStrategyResolver resolver = mock(MaskStrategyResolver.class);
        when(resolver.resolve(anyLong(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> SensitiveMasker.maskIp(inv.getArgument(2)));
        return new AuditLogExportTask(svc, resolver);
    }

    @Test
    void declaresExpectedMetadata() {
        AuditLogExportTask task = taskWith(mock(AuditLogService.class));
        assertEquals("audit-logs", task.code());
        assertEquals("审计日志", task.displayName());
        assertEquals("audit-log", task.fileName());
        assertFalse(task.headers().isEmpty());
    }

    @Test
    void headerAndRowWidthsAreConsistentAndMasked() {
        AuditLogService svc = mock(AuditLogService.class);
        when(svc.query(any(), anyBoolean())).thenReturn(new PageResult<>(1L, 10L, 1L, List.of(sampleView())));
        AuditLogExportTask task = taskWith(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals(1, rows.size());
        assertEquals(task.headers().size(), rows.get(0).size(), "行宽必须与表头一致");
        assertEquals("audit", rows.get(0).get(1), "模块列");
        assertEquals("READ", rows.get(0).get(2), "操作类型列");
        assertTrue(rows.get(0).get(5).contains("*"), "IP 列应被掩码，实际值: " + rows.get(0).get(5));
        assertEquals("2001", rows.get(0).get(0), "日志ID 列");
    }

    @Test
    void queriesWithSensitiveHiddenRegardlessOfCaller() {
        AuditLogService svc = mock(AuditLogService.class);
        when(svc.query(any(), anyBoolean())).thenReturn(new PageResult<>(1L, 10L, 0L, List.of()));
        AuditLogExportTask task = taskWith(svc);

        task.rows(Map.of("moduleCode", "audit", "startTime", "2026-10-01T00:00:00"));

        // 第二参 sensitiveVisible 必须为 false（导出一律脱敏）
        org.mockito.ArgumentCaptor<Boolean> visible = org.mockito.ArgumentCaptor.forClass(Boolean.class);
        verify(svc).query(any(), visible.capture());
        assertEquals(Boolean.FALSE, visible.getValue(), "导出查询必须 sensitiveVisible=false");
    }
}
