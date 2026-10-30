package com.acme.scaffold.system.export;

import com.acme.scaffold.system.log.LoginLogService;
import com.acme.scaffold.system.log.LoginLogView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LoginLogExportTask} 单元测试：验证「导出中心模板注册」契约与参数解析。
 *
 * <p>重点锁定：表头与行宽一致（否则 CSV/XLSX 会错列）、筛选参数透传、ISO 时间解析与非法值容错。
 * IP 的脱敏责任在 {@code LoginLogService#listForExport}，本类只做透传，故此处直接使用已掩码的视图值。</p>
 */
class LoginLogExportTaskTest {

    private static LoginLogView sampleView() {
        return new LoginLogView(1001L, 9L, "admin", "PASSWORD", "SUCCESS", null,
                "192.168.*.*", "Mozilla/5.0", "trace-abc", LocalDateTime.of(2026, 10, 16, 9, 30));
    }

    @Test
    void declaresExpectedMetadata() {
        LoginLogExportTask task = new LoginLogExportTask(mock(LoginLogService.class));
        assertEquals("login-logs", task.code());
        assertEquals("登录日志", task.displayName());
        assertEquals("login-log", task.fileName());
        // 单表结构（headers 非空）→ 导出中心自动支持 CSV + XLSX
        assertFalse(task.headers().isEmpty());
    }

    @Test
    void headerAndRowWidthsAreConsistent() {
        LoginLogService svc = mock(LoginLogService.class);
        when(svc.listForExport(any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(sampleView()));
        LoginLogExportTask task = new LoginLogExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals(1, rows.size());
        assertEquals(task.headers().size(), rows.get(0).size(), "行宽必须与表头一致");
        assertEquals("192.168.*.*", rows.get(0).get(5), "IP 列应为已掩码值");
        assertEquals("admin", rows.get(0).get(1));
        assertEquals("", rows.get(0).get(4), "null 失败码应归一为空串");
    }

    @Test
    void passesFilterParamsAndParsesIsoTime() {
        LoginLogService svc = mock(LoginLogService.class);
        when(svc.listForExport(any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        LoginLogExportTask task = new LoginLogExportTask(svc);

        task.rows(Map.of(
                "username", "admin",
                "result", "FAILURE",
                "startTime", "2026-10-01T00:00:00",
                "endTime", "2026-10-16T12:00:00"));

        ArgumentCaptor<LocalDateTime> begin = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(svc).listForExport(eq("admin"), eq("FAILURE"), any(), any(), any(), any(),
                begin.capture(), end.capture(), anyInt());
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), begin.getValue());
        assertEquals(LocalDateTime.of(2026, 10, 16, 12, 0), end.getValue());
    }

    @Test
    void invalidTimeParamIsIgnoredAsNull() {
        LoginLogService svc = mock(LoginLogService.class);
        when(svc.listForExport(any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());
        LoginLogExportTask task = new LoginLogExportTask(svc);

        task.rows(Map.of("startTime", "not-a-date"));

        ArgumentCaptor<LocalDateTime> begin = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(svc).listForExport(any(), any(), any(), any(), any(), any(),
                begin.capture(), any(), anyInt());
        assertNull(begin.getValue(), "非法时间应被忽略（返回 null 交由服务端补默认区间）");
    }
}
