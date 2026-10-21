package com.acme.scaffold.workbench.report.export;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.export.XlsxSheet;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.workbench.report.dto.ReportCategoryStatVO;
import com.acme.scaffold.workbench.report.dto.ReportQuery;
import com.acme.scaffold.workbench.report.dto.ReportRange;
import com.acme.scaffold.workbench.report.dto.ReportSeriesPointVO;
import com.acme.scaffold.workbench.report.dto.ReportStatusStatVO;
import com.acme.scaffold.workbench.report.dto.ReportSummaryVO;
import com.acme.scaffold.workbench.report.dto.ReportType;
import com.acme.scaffold.workbench.report.dto.ReportVO;
import com.acme.scaffold.workbench.report.service.ReportService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReportExportTask} 单测：权限兜底 / 参数解析 / 多段 CSV 产出。
 *
 * <p>ReportService 以 Mockito 替身（DB 聚合路径交 MySQL 集成测试）；
 * CSV 字节经真实 {@link ReportCsvWriter} + {@code CsvExporter} 产出后断言结构。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportExportTaskTest {

    @Mock
    private ReportService reportService;

    @Mock
    private SecurityContextFacade securityContextFacade;

    private ReportExportTask task;

    @BeforeEach
    void setUp() {
        task = new ReportExportTask(reportService, securityContextFacade);
    }

    private static CurrentPrincipal principal(boolean withReportRead) {
        Set<String> permissions = withReportRead ? Set.of("report:read", "system:export:read") : Set.of("system:export:read");
        return new CurrentPrincipal(1L, "tester", "测试员", 0L, "pc-admin", 1, Set.of("ADMIN"), permissions);
    }

    private static ReportVO usageVo() {
        return new ReportVO(ReportType.USAGE.name(), "使用报表", ReportRange.LAST_7D.name(),
                "2026-10-10 00:00:00", "2026-10-16 23:59:59",
                new ReportSummaryVO(100L, 20L, 95.5, 5L, 120L, 10L, 900L, 600L),
                List.of(new ReportSeriesPointVO("10-15", 30L, 8L, 2L)),
                List.of(new ReportCategoryStatVO("user", 60L, 60.0)),
                List.of(new ReportStatusStatVO("成功", 95L, 95.0)),
                List.of(),
                null);
    }

    @Test
    @DisplayName("持有 report:read：产出带 BOM 的多段 CSV，且查询参数正确传入 ReportService")
    void csvWithPermissionProducesReportCsv() {
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.of(principal(true)));
        when(reportService.report(any(ReportQuery.class))).thenReturn(usageVo());

        byte[] csv = task.csv(Map.of("type", "USAGE", "rangeType", "LAST_7D",
                "module", "user", "keyword", "订单"));
        String text = new String(csv, StandardCharsets.UTF_8);

        assertTrue(text.startsWith("\uFEFF"), "须带 BOM");
        assertTrue(text.contains("报表管理导出"), "含标题段");
        assertTrue(text.contains("访问量,100"), "含汇总段");
        assertTrue(text.contains("类别分布"), "含类别分布段");

        ArgumentCaptor<ReportQuery> captor = ArgumentCaptor.forClass(ReportQuery.class);
        verify(reportService).report(captor.capture());
        assertEquals(new ReportQuery(ReportType.USAGE, ReportRange.LAST_7D, null, null,
                        "user", null, "订单", null, null),
                captor.getValue());
    }

    @Test
    @DisplayName("缺 report:read：抛 FORBIDDEN，且不触达 ReportService（防权限放大）")
    void csvWithoutReportReadIsForbidden() {
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.of(principal(false)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> task.csv(Map.of("type", "USAGE")));

        assertTrue(ex.getMessage().contains("report:read"), "错误信息指明所需权限");
        verify(reportService, never()).report(any());
    }

    @Test
    @DisplayName("未登录主体：同样拒绝")
    void csvWithoutPrincipalIsForbidden() {
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> task.csv(Map.of()));
        verify(reportService, never()).report(any());
    }

    @Test
    @DisplayName("参数宽容：非法枚举回退默认（type→USAGE、rangeType→LAST_7D），success 仅 true/1 生效")
    void invalidParamsFallBackToDefaults() {
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.of(principal(true)));
        when(reportService.report(any(ReportQuery.class))).thenReturn(usageVo());

        task.csv(Map.of("type", "NOT_A_TYPE", "rangeType", "", "success", "yes"));
        task.csv(Map.of("success", "TRUE"));

        ArgumentCaptor<ReportQuery> captor = ArgumentCaptor.forClass(ReportQuery.class);
        verify(reportService, org.mockito.Mockito.times(2)).report(captor.capture());

        ReportQuery first = captor.getAllValues().get(0);
        assertEquals(ReportType.USAGE, first.type());
        assertEquals(ReportRange.LAST_7D, first.rangeType());
        assertEquals(null, first.success(), "yes 不是合法布尔值 → null（不筛选）");

        ReportQuery second = captor.getAllValues().get(1);
        assertEquals(Boolean.TRUE, second.success(), "TRUE 归一为 true");
    }

    @Test
    @DisplayName("headers/rows 为空（多段结构覆写 csv()，不走单表契约）")
    void headersAndRowsAreEmpty() {
        assertTrue(task.headers().isEmpty());
        assertTrue(task.rows(Map.of()).isEmpty());
    }

    @Test
    @DisplayName("声明 XLSX 能力：xlsxSheets 产出多 Sheet 模型，查询参数与 CSV 路径同源")
    void xlsxSheetsWithPermissionProducesSheets() {
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.of(principal(true)));
        when(reportService.report(any(ReportQuery.class))).thenReturn(usageVo());

        assertTrue(task.supportsXlsx(), "多段结构模板提供 xlsxSheets 后应声明支持 XLSX");
        List<XlsxSheet> sheets = task.xlsxSheets(Map.of("type", "USAGE", "rangeType", "LAST_7D"));

        assertEquals(List.of("汇总", "类别分布", "状态分布", "趋势"), sheets.stream().map(XlsxSheet::name).toList());
        assertEquals(List.of("指标", "数值"), sheets.get(0).headers());
        verify(reportService).report(any(ReportQuery.class));
    }

    @Test
    @DisplayName("XLSX 路径权限兜底：缺 report:read 时同样拒绝（防权限放大）")
    void xlsxSheetsWithoutReportReadIsForbidden() {
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.of(principal(false)));

        BusinessException ex = assertThrows(BusinessException.class, () -> task.xlsxSheets(Map.of()));

        assertTrue(ex.getMessage().contains("report:read"), "错误信息指明所需权限");
        verify(reportService, never()).report(any());
    }
}
