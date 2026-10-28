package com.acme.scaffold.system.dashboard;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.export.CsvExporter;
import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.common.export.XlsxSheet;
import com.acme.scaffold.common.tenant.TenantIgnorable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 运营大屏数据导出（导出中心模板，M32；承接 M27 报表大屏）。
 *
 * <p>经导出中心统一调度：模板由 Spring 容器自动收集，前端零维护清单、后端零迁移。
 * 复用 {@link DashboardService#overview()} 与 {@link DashboardService#trend(int)} 的同一份聚合口径，
 * 保证「看板页展示」与「导出文件」数据一致，无重复统计逻辑。</p>
 *
 * <p><b>租户语义</b>：标注 {@link TenantIgnorable}，导出为平台级全局统计，不受当前租户过滤
 * （与 {@code DashboardController} 的 {@code @TenantIgnorable} 同义；切面在代理方法执行期内置位旁路）。</p>
 *
 * <p><b>权限兜底</b>：导出中心下载端点仅校验 {@code system:export:read}，而运营大屏数据自身另需
 * {@code system:dashboard:read}（见 {@code DashboardController} 类级 {@code @PreAuthorize}）。
 * 若不在此复核，持有导出权限但无看板权限的角色可经本入口绕过 {@code system:dashboard:read}
 * 拉取平台全局运营数据（权限放大）。故 {@link #csv(Map)} 与 {@link #xlsxSheets(Map)} 内显式校验当前主体权限。</p>
 *
 * <p><b>多段结构</b>：数据为「总览快照 + 六序列趋势」的多维结构，无法用「单表头 + 行」表达，
 * 故覆写 {@link #csv(Map)} 自产 CSV 字节（单列扁平化）；XLSX 则经 {@link #xlsxSheets(Map)}
 * 将各段映射为多 Sheet 工作簿（总览一页 + 六趋势各一页），权限兜底对两种格式同样生效。</p>
 */
@Component
@RequiredArgsConstructor
public class DashboardExportTask implements ExportTask {

    /** 模板编码（导出中心路由键）。 */
    public static final String CODE = "dashboard";

    /** 运营大屏数据自身权限码（导出中心端点权限之外的兜底校验）。 */
    private static final String DASHBOARD_READ = "system:dashboard:read";

    /** 趋势回溯天数默认与上限（与 DashboardController / DashboardRules 对齐）。 */
    private static final int DEFAULT_TREND_DAYS = 30;

    private final DashboardService dashboardService;
    private final SecurityContextFacade securityContextFacade;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String displayName() {
        return "运营大屏数据导出";
    }

    @Override
    public String fileName() {
        return "dashboard-export";
    }

    /** 多段结构不使用单表头契约（见类注释），返回空列表。 */
    @Override
    public List<String> headers() {
        return List.of();
    }

    /** 多段结构不使用行契约（见类注释），返回空列表。 */
    @Override
    public List<List<String>> rows(Map<String, String> params) {
        return List.of();
    }

    @Override
    @TenantIgnorable("平台级全局统计，跨租户聚合")
    public byte[] csv(Map<String, String> params) {
        requireDashboardRead();
        int days = parseDays(params);
        DashboardOverview overview = dashboardService.overview();
        DashboardTrend trend = dashboardService.trend(days);

        List<String> headers = List.of("数据段", "标签", "值");
        List<List<String>> rows = new ArrayList<>();
        // 总览快照段
        for (String[] metric : overviewMetrics(overview)) {
            rows.add(List.of("总览", metric[0], metric[1]));
        }
        // 六趋势段（每段一行一日期）
        addTrendRows(rows, "趋势-新增用户", trend.newUsers());
        addTrendRows(rows, "趋势-新增租户", trend.newTenants());
        addTrendRows(rows, "趋势-活跃租户", trend.activeTenants());
        addTrendRows(rows, "趋势-登录日志", trend.loginLogs());
        addTrendRows(rows, "趋势-操作日志", trend.operationLogs());
        addTrendRows(rows, "趋势-通知发送", trend.notifications());

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            CsvExporter.write(bos, headers, rows);
        } catch (IOException e) {
            // ByteArrayOutputStream 不抛 IOException，防御性保留
            throw new IllegalStateException("运营大屏 CSV 生成失败", e);
        }
        return bos.toByteArray();
    }

    /** 多段结构模板经多 Sheet 提供 XLSX 能力（权限兜底同 CSV）。 */
    @Override
    public boolean supportsXlsx() {
        return true;
    }

    @Override
    @TenantIgnorable("平台级全局统计，跨租户聚合")
    public List<XlsxSheet> xlsxSheets(Map<String, String> params) {
        requireDashboardRead();
        int days = parseDays(params);
        DashboardOverview overview = dashboardService.overview();
        DashboardTrend trend = dashboardService.trend(days);

        List<XlsxSheet> sheets = new ArrayList<>();
        // 总览页：指标 / 值
        List<String> ovHeaders = List.of("指标", "值");
        List<List<String>> ovRows = new ArrayList<>();
        for (String[] metric : overviewMetrics(overview)) {
            ovRows.add(List.of(metric[0], metric[1]));
        }
        sheets.add(new XlsxSheet("总览", ovHeaders, ovRows));
        // 六趋势页：日期 / 数值
        sheets.add(trendSheet("新增用户趋势", "新增用户数", trend.newUsers()));
        sheets.add(trendSheet("新增租户趋势", "新增租户数", trend.newTenants()));
        sheets.add(trendSheet("活跃租户趋势", "活跃租户数", trend.activeTenants()));
        sheets.add(trendSheet("登录日志趋势", "登录日志数", trend.loginLogs()));
        sheets.add(trendSheet("操作日志趋势", "操作日志数", trend.operationLogs()));
        sheets.add(trendSheet("通知发送趋势", "通知发送数", trend.notifications()));
        return sheets;
    }

    /** 总览快照按展示顺序展开为「指标名, 值」对（值统一转字符串，便于 CSV/XLSX 同口径）。 */
    private static List<String[]> overviewMetrics(DashboardOverview o) {
        return List.of(
                new String[]{"租户总数", String.valueOf(o.tenantCount())},
                new String[]{"用户总数", String.valueOf(o.userCount())},
                new String[]{"角色总数", String.valueOf(o.roleCount())},
                new String[]{"菜单总数", String.valueOf(o.menuCount())},
                new String[]{"在线会话数", String.valueOf(o.onlineSessionCount())},
                new String[]{"幂等记录总数", String.valueOf(o.idempotencyRecordCount())},
                new String[]{"操作日志总数", String.valueOf(o.operationLogCount())},
                new String[]{"今日登录数", String.valueOf(o.loginLogTodayCount())},
                new String[]{"N+1疑似累计次数", String.valueOf(o.nPlusOneSuspectTotal())},
                new String[]{"N+1疑似留存条数", String.valueOf(o.nPlusOneSuspectRetained())},
                new String[]{"最近N+1越限时间(毫秒)", String.valueOf(o.lastBreachAtMillis())},
                new String[]{"链路追踪启用", String.valueOf(o.traceRecorderEnabled())},
                new String[]{"链路追踪留存条数", String.valueOf(o.traceRetained())},
                new String[]{"链路追踪累计条数", String.valueOf(o.traceTotal())},
                new String[]{"链路追踪错误条数", String.valueOf(o.traceErrorTotal())},
                new String[]{"快照生成时间(毫秒)", String.valueOf(o.generatedAt())});
    }

    /** 将单条趋势序列写入 CSV 行（数据段固定 + 每点一行）。 */
    private static void addTrendRows(List<List<String>> rows, String section, List<TrendPoint> points) {
        if (points == null) {
            return;
        }
        for (TrendPoint p : points) {
            String date = (p.date() == null) ? "" : p.date().toString();
            rows.add(List.of(section, date, String.valueOf(p.value())));
        }
    }

    /** 构造单条趋势工作表（列：日期 / 数值）。 */
    private static XlsxSheet trendSheet(String name, String valueLabel, List<TrendPoint> points) {
        List<String> headers = List.of("日期", valueLabel);
        List<List<String>> rows = new ArrayList<>();
        if (points != null) {
            for (TrendPoint p : points) {
                String date = (p.date() == null) ? "" : p.date().toString();
                rows.add(List.of(date, String.valueOf(p.value())));
            }
        }
        return new XlsxSheet(name, headers, rows);
    }

    /** 解析 trend 天数参数（缺省 30；越界/非法宽容回退默认，与 DashboardRules 钳制一致）。 */
    private static int parseDays(Map<String, String> params) {
        String raw = params.get("days");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_TREND_DAYS;
        }
        try {
            return DashboardRules.clampDays(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_TREND_DAYS;
        }
    }

    /** 权限兜底：当前主体须持有 system:dashboard:read（与 DashboardController 类级 @PreAuthorize 同码）。 */
    private void requireDashboardRead() {
        CurrentPrincipal principal = securityContextFacade.getCurrentPrincipal().orElse(null);
        if (principal == null || !principal.hasPermission(DASHBOARD_READ)) {
            throw BusinessException.of(CommonErrorCode.FORBIDDEN,
                    "无运营大屏导出权限（需 " + DASHBOARD_READ + "）");
        }
    }
}
