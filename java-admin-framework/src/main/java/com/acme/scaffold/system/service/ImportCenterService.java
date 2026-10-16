package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.importer.CsvDataReader;
import com.acme.scaffold.common.importer.ImportFormat;
import com.acme.scaffold.common.importer.ImportResult;
import com.acme.scaffold.common.importer.ImportRow;
import com.acme.scaffold.common.importer.ImportTask;
import com.acme.scaffold.common.importer.RowError;
import com.acme.scaffold.common.importer.TabularData;
import com.acme.scaffold.common.importer.XlsxDataReader;
import com.acme.scaffold.system.importer.config.ImportProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入中心运行时：收集全部 {@link ImportTask} Bean，统一完成「格式识别 → 解析 → 表头校验 → 逐行校验/导入」。
 *
 * <p>与导出中心（{@code common.export.ExportTask} 模板注册模式）对称：各业务模块只需声明一个
 * 实现 {@link ImportTask} 的 Spring Bean 即可被自动发现，无需为每种数据手写上传接口。</p>
 *
 * <p>格式无关：CSV 与 XLSX 均解析为同一份 {@link TabularData}（表头 + 行），
 * 因此同一模板天然同时支持两种格式上传。</p>
 *
 * <p>导入语义（逐行独立）：单行失败不影响其余行，失败明细随结果返回；预检模式（dryRun）
 * 只做校验不落库。详见 {@link ImportTask} 与 {@link ImportResult} 的说明。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportCenterService {

    private final List<ImportTask> importTasks;
    private final ImportProperties importProperties;

    /** 全部可用导入模板（供模板列表与模板下载使用）。 */
    public List<ImportTask> tasks() {
        return importTasks;
    }

    /**
     * 按编码取导入模板。
     *
     * @throws BusinessException 编码不存在时抛 404 业务错误
     */
    public ImportTask task(String code) {
        return importTasks.stream()
                .filter(t -> t.code().equals(code))
                .findFirst()
                .orElseThrow(() -> BusinessException.of(CommonErrorCode.NOT_FOUND, "未知的导入模板: " + code));
    }

    /**
     * 解析上传文件并逐行导入（或仅预检）。
     *
     * @param code     模板编码
     * @param fileName 上传文件原始名（用于识别 CSV / XLSX）
     * @param content  上传文件字节
     * @param dryRun   为 true 时只校验不落库
     * @return 导入结果（统计 + 失败明细）
     */
    public ImportResult importData(String code, String fileName, byte[] content, boolean dryRun) {
        ImportTask task = task(code);
        ImportFormat format = ImportFormat.detect(fileName);
        if (format == null) {
            throw BusinessException.badRequest("仅支持 CSV / XLSX 文件，当前文件：" + fileName);
        }
        if (content == null || content.length == 0) {
            throw BusinessException.badRequest("上传文件内容为空");
        }

        TabularData data = parse(format, content);
        if (data.isEmpty()) {
            throw BusinessException.badRequest("文件无有效内容（至少需一行表头）");
        }
        validateHeaders(task, data.headers());

        int maxRows = importProperties.getMaxRows();
        if (data.rowCount() > maxRows) {
            throw BusinessException.badRequest(
                    "导入行数(" + data.rowCount() + ")超出上限(" + maxRows + ")，请拆分文件后分批导入");
        }

        List<String> taskHeaders = task.headers();
        List<RowError> errors = new ArrayList<>();
        int processed = 0;
        int success = 0;
        int failed = 0;
        for (int i = 0; i < data.rows().size(); i++) {
            // 行号从 1 起（不含表头），便于用户对照表格定位
            int rowNo = i + 1;
            Map<String, String> values = toValues(taskHeaders, data.rows().get(i));
            if (isBlankRow(values)) {
                // 跳过用户编辑后遗留的全空行（Excel 常见），不计入统计
                continue;
            }
            processed++;
            ImportRow row = new ImportRow(rowNo, values);
            List<String> rowErrors = new ArrayList<>(task.validate(row));
            if (rowErrors.isEmpty()) {
                if (dryRun) {
                    success++;
                } else if (applyRow(task, row, rowErrors)) {
                    success++;
                }
            }
            if (rowErrors.isEmpty()) {
                continue;
            }
            // 失败行数按真实值累加；错误明细受上限约束，超出仅记录日志
            failed++;
            if (errors.size() < importProperties.getMaxErrors()) {
                errors.add(new RowError(rowNo, String.join("；", rowErrors)));
            } else {
                log.warn("导入 {} 第 {} 行失败（错误明细已达上限，仅计数）: {}", code, rowNo, rowErrors);
            }
        }
        log.info("导入完成：模板={}，格式={}，预检={}，处理 {} 行，成功 {} 行，失败 {} 行",
                code, format, dryRun, processed, success, failed);
        return new ImportResult(dryRun, processed, success, failed, errors);
    }

    /** 落库单行；异常转为该行错误信息（不中断其余行）。 */
    private boolean applyRow(ImportTask task, ImportRow row, List<String> rowErrors) {
        try {
            task.importRow(row);
            return true;
        } catch (BusinessException e) {
            rowErrors.add(e.getMessage());
        } catch (RuntimeException e) {
            log.warn("导入行 {} 失败: {}", row.rowNo(), e.getMessage(), e);
            rowErrors.add("导入失败：" + e.getMessage());
        }
        return false;
    }

    private TabularData parse(ImportFormat format, byte[] content) {
        try {
            return (format == ImportFormat.CSV)
                    ? CsvDataReader.read(content)
                    : XlsxDataReader.read(content);
        } catch (IllegalArgumentException e) {
            // 解析类异常统一转为 400，避免以 500 暴露内部堆栈
            throw BusinessException.badRequest(e.getMessage());
        }
    }

    /** 上传文件表头必须与模板表头逐列一致（顺序敏感），否则用户极易错列导入。 */
    private void validateHeaders(ImportTask task, List<String> actual) {
        List<String> expected = task.headers();
        if (actual.size() != expected.size()) {
            throw BusinessException.badRequest("表头列数不匹配：模板需 " + expected.size() + " 列（"
                    + String.join(" / ", expected) + "），文件为 " + actual.size() + " 列");
        }
        for (int c = 0; c < expected.size(); c++) {
            String a = actual.get(c) == null ? "" : actual.get(c).trim();
            if (!expected.get(c).equals(a)) {
                throw BusinessException.badRequest("第 " + (c + 1) + " 列表头不匹配：模板为「"
                        + expected.get(c) + "」，文件为「" + a + "」。请先下载模板按格式填写");
            }
        }
    }

    private static Map<String, String> toValues(List<String> headers, List<String> cells) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int c = 0; c < headers.size(); c++) {
            values.put(headers.get(c), c < cells.size() ? cells.get(c) : "");
        }
        return values;
    }

    private static boolean isBlankRow(Map<String, String> values) {
        return values.values().stream().allMatch(v -> v == null || v.isBlank());
    }
}
