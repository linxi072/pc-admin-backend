package com.acme.scaffold.system.dashboard;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.export.XlsxSheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DashboardExportTask} 单元测试（M32 运营大屏数据导出）。
 *
 * <p>重点锁定：导出中心模板注册契约（code/displayName/fileName、空 headers/rows 多段结构）、
 * 权限兜底（须持 {@code system:dashboard:read}，防权限放大）、days 参数宽容回退
 * （非法/越界由 {@link DashboardRules#clampDays} 钳制）、CSV 带 BOM 多段产出、
 * XLSX 多 Sheet（总览 + 六趋势）口径与 {@link DashboardService} 一致。</p>
 *
 * <p>DashboardService 以 Mockito 替身（DB 聚合路径交集成测试），CSV/XLSX 经真实
 * {@code CsvExporter}/{@code XlsxDataExporter} 产出后断言结构。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardExportTaskTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private SecurityContextFacade securityContextFacade;

    private DashboardExportTask task;

    @BeforeEach
    void setUp() {
        task = new DashboardExportTask(dashboardService, securityContextFacade);
    }

    private static CurrentPrincipal principal(boolean withDashboardRead) {
        Set<String> permissions = withDashboardRead
                ? Set.of("system:dashboard:read", "system:export:read")
                : Set.of("system:export:read");
        return new CurrentPrincipal(1L, "tester", "测试员", 0L, "pc-admin", 1, Set.of("ADMIN"), permissions);
    }

    /** 总览快照样本（8 项系统域计数；跨模块可观测指标回落 EMPTY 全零，与 DashboardRules 口径一致）。 */
    private static DashboardOverview sampleOverview() {
        return DashboardRules.buildOverview(
                10, 200, 5, 50, 3, 1000, 5000, 120, 1_700_000_001_000L);
    }

    /** 趋势样本：六序列均取同一份 2 日点，便于断言行数。 */
    private static DashboardTrend sampleTrend() {
        List<TrendPoint> points = List.of(
                new TrendPoint(LocalDate.of(2026, 10, 15), 3L),
                new TrendPoint(LocalDate.of(2026, 10, 16), 5L));
        return new DashboardTrend(30, points, points, points, points, points, points, 1_700_000_001_000L);
    }

    @Test
    @DisplayName("声明元数据：code/displayName/fileName，且 headers/rows 为空（多段结构）")
    void declaresExpectedMetadata() {
        assertEquals("dashboard", task.code());
        assertEquals("运营大屏数据导出", task.displayName());
        assertEquals("dashboard-export", task.fileName());
        assertTrue(task.headers().isEmpty());
        assertTrue(task.rows(Map.of()).isEmpty());
        assertTrue(task.supportsXlsx(), "多段结构模板提供 xlsxSheets 后应声明支持 XLSX");
    }

    @Test
    @DisplayName("持有 system:dashboard:read：产出带 BOM 的多段 CSV，含总览与六趋势段")
    void csvWithPermissionProducesDashboardCsv() {
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.of(principal(true)));
        when(dashboardService.overview()).thenReturn(sampleOverview());
        when(dashboardService.trend(anyInt())).thenReturn(sampleTrend());

        byte[] csv = task.csv(Map.of("days", "30"));
        String text = new String(csv, StandardCharsets.UTF_8);

        assertTrue(text.startsWith("﻿"), "须带 UTF-8 BOM");
        assertTrue(text.contains("总览"), "含总览段标识");
        assertTrue(text.contains("租户总数"), "含总览指标标签");
        assertTrue(text.contains("趋势-新增用户"), "含新增用户趋势段");
        assertTrue(text.contains("2026-10-15"), "含趋势日期");
        verify(dashboardService).trend(30);
    }

    @Test
    @DisplayName("缺 system:dashboard:read：抛 FORBIDDEN，且不触达 DashboardService（防权限放大）")
    void csvWithoutDashboardReadIsForbidden() {
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.of(principal(false)));

        BusinessException ex = assertThrows(BusinessException.class, () -> task.csv(Map.of("days", "30")));

        assertTrue(ex.getMessage().contains("system:dashboard:read"), "错误信息指明所需权限");
        verify(dashboardService, never()).overview();
        verify(dashboardService, never()).trend(anyInt());
    }

    @Test
    @DisplayName("未登录主体：同样拒绝")
    void csvWithoutPrincipalIsForbidden() {
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> task.csv(Map.of()));
        verify(dashboardService, never()).overview();
    }

    @Test
    @DisplayName("days 参数宽容：非法/越界回落默认钳制（与 DashboardRules.clampDays 一致）")
    void invalidDaysFallsBack() {
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.of(principal(true)));
        when(dashboardService.overview()).thenReturn(sampleOverview());
        when(dashboardService.trend(anyInt())).thenReturn(sampleTrend());

        task.csv(Map.of("days", "abc"));
        task.csv(Map.of("days", "9999"));

        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(dashboardService, org.mockito.Mockito.times(2)).trend(captor.capture());
        assertEquals(30, captor.getAllValues().get(0), "非法 days → 默认 30");
        assertEquals(90, captor.getAllValues().get(1), "越界 days → 钳制 90");
    }

    @Test
    @DisplayName("XLSX 多 Sheet：总览页 + 六趋势页，段名与行数与口径一致")
    void xlsxSheetsWithPermissionProducesSheets() {
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.of(principal(true)));
        when(dashboardService.overview()).thenReturn(sampleOverview());
        when(dashboardService.trend(anyInt())).thenReturn(sampleTrend());

        List<XlsxSheet> sheets = task.xlsxSheets(Map.of("days", "30"));

        assertEquals(List.of("总览", "新增用户趋势", "新增租户趋势", "活跃租户趋势",
                        "登录日志趋势", "操作日志趋势", "通知发送趋势"),
                sheets.stream().map(XlsxSheet::name).toList());
        // 总览页 16 项指标
        assertEquals(16, sheets.get(0).rows().size());
        assertEquals(List.of("指标", "值"), sheets.get(0).headers());
        // 趋势页 2 行（样本 2 日）
        assertEquals(2, sheets.get(1).rows().size());
        assertEquals(List.of("日期", "新增用户数"), sheets.get(1).headers());
        verify(dashboardService).trend(30);
    }

    @Test
    @DisplayName("XLSX 路径权限兜底：缺 system:dashboard:read 时同样拒绝（防权限放大）")
    void xlsxSheetsWithoutDashboardReadIsForbidden() {
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.of(principal(false)));

        BusinessException ex = assertThrows(BusinessException.class, () -> task.xlsxSheets(Map.of()));

        assertTrue(ex.getMessage().contains("system:dashboard:read"), "错误信息指明所需权限");
        verify(dashboardService, never()).overview();
        verify(dashboardService, never()).trend(anyInt());
    }
}
