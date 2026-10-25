package com.acme.scaffold.system.export;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.dto.AuditLogQuery;
import com.acme.scaffold.common.audit.AuditLogService;
import com.acme.scaffold.common.audit.AuditLogView;
import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.common.sensitive.SensitiveType;
import com.acme.scaffold.system.maskstrategy.MaskStrategyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审计日志导出（导出中心内置模板，M20 增强）。
 *
 * <p><b>设计意图</b>：审计日志是合规留痕核心数据，管理员需导出做离线归档。
 * 本类仅实现 {@link ExportTask} 契约即可被导出中心自动发现——<b>无需新增接口、迁移或前端页面</b>，
 * 与 {@link LoginLogExportTask} 对称。筛选参数（module/type/operatorName/traceId/keyword/时间区间）由导出中心透传。</p>
 *
 * <p><b>脱敏</b>：导出文件会离开系统边界，故一律以 {@code sensitiveVisible=false} 查询（摘要按既有规则脱敏），
 * 且 IP 经 {@link MaskStrategyResolver} 兜底掩码（即便调用者持有 {@code system:audit:sensitive} 也不放行原文）。
 * 未命中任何策略时回落默认掩码（SensitiveType.IP），与 M20 既有口径一致；管理员可在「脱敏策略中心」
 * 注册 field_key={@code audit.ip} 的策略，集中管控 IP 的脱敏算法与角色豁免。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogExportTask implements ExportTask {

    /** 单次导出取数上限（与查询侧默认口径一致）。 */
    private static final int MAX_ROWS = 10_000;

    /** 导出为系统级行为、跨租户归档，按平台租户(0)解析脱敏策略；导出文件对所有人统一脱敏（无豁免角色）。 */
    private static final long EXPORT_TENANT = 0L;

    private final AuditLogService auditLogService;
    private final MaskStrategyResolver maskStrategyResolver;

    @Override
    public String code() {
        return "audit-logs";
    }

    @Override
    public String displayName() {
        return "审计日志";
    }

    @Override
    public String fileName() {
        return "audit-log";
    }

    @Override
    public List<String> headers() {
        return List.of("日志ID", "模块", "操作类型", "操作名称", "操作人", "IP", "请求方法",
                "请求路径", "结果码", "成功", "耗时(ms)", "发生时间");
    }

    @Override
    public List<List<String>> rows(Map<String, String> params) {
        AuditLogQuery query = new AuditLogQuery(1, MAX_ROWS,
                params.get("moduleCode"), params.get("operationType"), null, params.get("operatorName"),
                null, params.get("traceId"), params.get("keyword"), null,
                parseTime(params.get("startTime")), parseTime(params.get("endTime")), null, null);
        // 导出一律脱敏（数据离开系统边界）
        PageResult<AuditLogView> page = auditLogService.query(query, false);
        return page.records().stream().map(v -> List.of(
                str(v.id()),
                str(v.moduleCode()),
                str(v.operationType()),
                str(v.operationName()),
                str(v.operatorName()),
                maskStrategyResolver.resolve(EXPORT_TENANT, "audit.ip", v.ipAddress(),
                        SensitiveType.IP, Set.of()),
                str(v.requestMethod()),
                str(v.requestPath()),
                str(v.resultCode()),
                str(v.success()),
                str(v.durationMs()),
                str(v.occurredAt())
        )).toList();
    }

    /** 解析 ISO-8601 时间参数；空或非法一律返回 null（视为不加时间过滤，由服务端补默认区间）。 */
    private static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            log.warn("审计日志导出：时间参数格式非法，已忽略: {}", value);
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
