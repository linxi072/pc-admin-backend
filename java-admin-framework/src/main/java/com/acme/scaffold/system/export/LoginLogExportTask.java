package com.acme.scaffold.system.export;

import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.system.log.LoginLogService;
import com.acme.scaffold.system.log.LoginLogView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 登录日志导出（导出中心内置模板）。
 *
 * <p><b>设计意图</b>：登录日志是安全审计的核心数据，管理员需要将其导出做离线归档/合规留痕。
 * 本类仅实现 {@link ExportTask} 契约即可被导出中心自动发现并列举——<b>无需新增接口、迁移或前端页面</b>，
 * 是「模板注册」扩展点的最小示例（与 {@link FileListExportTask} 对称）。</p>
 *
 * <p><b>脱敏</b>：导出文件会离开系统边界，故 IP 一律掩码（见 {@code LoginLogService#listForExport}），
 * 即便调用者持有 {@code system:login-log:sensitive} 也不放行原文。筛选参数（username/result/...）
 * 由导出中心透传，可选项，缺省导出最近 7 天（与列表页默认口径一致）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLogExportTask implements ExportTask {

    /** 单次导出取数上限（与 {@code LoginLogService} 内部上限一致）。 */
    private static final int MAX_ROWS = 10_000;

    private final LoginLogService loginLogService;

    @Override
    public String code() {
        return "login-logs";
    }

    @Override
    public String displayName() {
        return "登录日志";
    }

    @Override
    public String fileName() {
        return "login-log";
    }

    @Override
    public List<String> headers() {
        return List.of("日志ID", "用户名", "登录类型", "结果", "失败码", "IP", "客户端UA", "链路ID", "发生时间");
    }

    @Override
    public List<List<String>> rows(Map<String, String> params) {
        List<LoginLogView> list = loginLogService.listForExport(
                params.get("username"),
                params.get("result"),
                params.get("loginType"),
                params.get("failureCode"),
                params.get("ipAddress"),
                params.get("keyword"),
                parseTime(params.get("startTime")),
                parseTime(params.get("endTime")),
                MAX_ROWS);
        return list.stream().map(v -> List.of(
                str(v.id()),
                str(v.username()),
                str(v.loginType()),
                str(v.result()),
                str(v.failureCode()),
                str(v.ipAddress()),
                str(v.userAgent()),
                str(v.traceId()),
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
            log.warn("登录日志导出：时间参数格式非法，已忽略: {}", value);
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
