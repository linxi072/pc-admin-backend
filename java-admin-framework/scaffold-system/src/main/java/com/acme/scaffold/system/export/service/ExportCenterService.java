package com.acme.scaffold.system.export.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.export.CsvExporter;
import com.acme.scaffold.common.export.ExportFormat;
import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.common.export.XlsxDataExporter;
import com.acme.scaffold.common.export.XlsxSheet;
import com.acme.scaffold.system.dto.ExportTemplateView;
import com.acme.scaffold.system.export.config.ExportProperties;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导出中心编排服务（D3：将 {@code ExportController} 内的导出编排逻辑下沉至此）。
 *
 * <p>职责：模板发现、格式解析、行数上限校验、CSV/XLSX 分流、文件字节生成。
 * 控制器只负责参数接收与响应包装（{@code ResponseEntity}），保持「薄控制器 + 厚服务」分层，便于离线单测。</p>
 */
@Service
public class ExportCenterService {

    private final List<ExportTask> exportTasks;
    private final ExportProperties exportProperties;

    public ExportCenterService(List<ExportTask> exportTasks, ExportProperties exportProperties) {
        this.exportTasks = exportTasks;
        this.exportProperties = exportProperties;
    }

    /** 列出全部已注册模板及其支持的格式。 */
    public List<ExportTemplateView> listTemplates() {
        return exportTasks.stream()
                .map(t -> new ExportTemplateView(t.code(), t.displayName(), t.fileName(), supportedFormats(t)))
                .toList();
    }

    /**
     * 按模板导出文件字节，连同文件名与格式一并返回，供控制器封装为 {@code ResponseEntity}。
     *
     * @param allParams 全量查询参数（含 {@code code} / {@code format}，二者在内部被消费）
     */
    public ExportedFile download(Map<String, String> allParams) {
        String code = allParams.get("code");
        ExportTask task = exportTasks.stream()
                .filter(t -> t.code().equals(code))
                .findFirst()
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND, "未知的导出模板: " + code));

        // 兼容旧调用：缺省 format 视为 CSV
        String rawFormat = allParams.get("format");
        ExportFormat format = (rawFormat == null || rawFormat.isBlank())
                ? ExportFormat.CSV
                : ExportFormat.from(rawFormat);
        if (format == null) {
            throw BusinessException.badRequest("不支持的导出格式: " + rawFormat);
        }

        // 查询参数全量透传（code / format 除外）：各模板按需取值。空串归一为 null，保持与旧版行为一致。
        Map<String, String> params = new LinkedHashMap<>();
        allParams.forEach((k, v) -> {
            if (!"code".equals(k) && !"format".equals(k)) {
                params.put(k, blankToNull(v));
            }
        });

        byte[] body = (format == ExportFormat.CSV) ? toCsv(task, params) : toXlsx(task, params);
        return new ExportedFile(body, task.fileName(), format);
    }

    /**
     * 生成 CSV 字节。
     *
     * <p>单表结构（{@code headers()} 非空）在此取一次数据行并完成行数上限校验，再自行序列化——
     * 不直接委托 {@link ExportTask#csv(Map)}，否则校验与序列化会各触发一次 {@code rows(params)}
     * 查询（多数模板是查库），平白翻倍。多段结构（报表）由模板自产字节、行数受其自身约束，
     * 仍走 {@link ExportTask#csv(Map)}。历史缺陷：CSV 分支曾完全不做上限校验。</p>
     */
    private byte[] toCsv(ExportTask task, Map<String, String> params) {
        List<String> headers = task.headers();
        if (headers.isEmpty()) {
            return task.csv(params);
        }
        List<List<String>> rows = task.rows(params);
        int maxRows = exportProperties.getMaxRows();
        if (rows.size() > maxRows) {
            throw BusinessException.badRequest(
                    "导出行数(" + rows.size() + ")超出上限(" + maxRows + ")，请缩小筛选范围后分批导出");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            CsvExporter.write(bos, headers, rows);
        } catch (IOException e) {
            // ByteArrayOutputStream 不抛 IOException，防御性保留
            throw new IllegalStateException("CSV 生成失败", e);
        }
        return bos.toByteArray();
    }

    /**
     * 生成 XLSX 字节。单表结构（{@code headers()} 非空）走单 Sheet 路径；
     * 多段结构走 {@link ExportTask#xlsxSheets(Map)} 多 Sheet 工作簿路径（未提供该能力则拒绝）。
     * 各 Sheet 数据行总数超 {@code app.export.max-rows} 上限则拒绝，避免单次导出耗尽内存。
     */
    private byte[] toXlsx(ExportTask task, Map<String, String> params) {
        List<String> headers = task.headers();
        if (!headers.isEmpty()) {
            List<List<String>> rows = task.rows(params);
            int maxRows = exportProperties.getMaxRows();
            if (rows.size() > maxRows) {
                throw BusinessException.badRequest(
                        "导出行数(" + rows.size() + ")超出上限(" + maxRows + ")，请缩小筛选范围后分批导出");
            }
            return XlsxDataExporter.export(headers, rows);
        }
        List<XlsxSheet> sheets = task.xlsxSheets(params);
        if (sheets.isEmpty()) {
            throw BusinessException.badRequest(
                    "模板「" + task.code() + "」未提供 XLSX 导出能力，请改用 CSV 导出");
        }
        int totalRows = sheets.stream().mapToInt(s -> s.rows().size()).sum();
        int maxRows = exportProperties.getMaxRows();
        if (totalRows > maxRows) {
            throw BusinessException.badRequest(
                    "导出行数(" + totalRows + ")超出上限(" + maxRows + ")，请缩小筛选范围后分批导出");
        }
        return XlsxDataExporter.exportWorkbook(sheets);
    }

    /** 支持 XLSX 与否由模板声明（单表结构默认支持；多段结构提供 xlsxSheets 后支持）。 */
    private static List<String> supportedFormats(ExportTask task) {
        return task.supportsXlsx() ? List.of("CSV", "XLSX") : List.of("CSV");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** 导出产物（字节 + 文件名 + 格式），供控制器封装为 {@code ResponseEntity}。 */
    public record ExportedFile(byte[] body, String fileName, ExportFormat format) {
    }
}
