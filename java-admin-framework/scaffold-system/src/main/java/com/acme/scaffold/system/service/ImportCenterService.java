package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.importer.CsvDataReader;
import com.acme.scaffold.common.importer.FailedRow;
import com.acme.scaffold.common.importer.ImportFormat;
import com.acme.scaffold.common.importer.ImportOutcome;
import com.acme.scaffold.common.importer.ImportResult;
import com.acme.scaffold.common.importer.ImportRow;
import com.acme.scaffold.common.importer.ImportTask;
import com.acme.scaffold.common.importer.MultiSegmentImportTask;
import com.acme.scaffold.common.importer.RowError;
import com.acme.scaffold.common.importer.Segment;
import com.acme.scaffold.common.importer.SegmentResult;
import com.acme.scaffold.common.importer.SegmentSpec;
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
import java.util.TreeMap;

/**
 * 导入中心运行时：收集全部 {@link ImportTask} Bean，统一完成「格式识别 → 解析 → 表头校验 → 逐行校验/导入」。
 *
 * <p>与导出中心（{@code common.export.ExportTask} 模板注册模式）对称：各业务模块只需声明一个
 * 实现 {@link ImportTask} 的 Spring Bean 即可被自动发现，无需为每种数据手写上传接口。</p>
 *
 * <p>格式无关：CSV 与 XLSX 均解析为同一份 {@link TabularData}（表头 + 行），
 * 因此同一模板天然同时支持两种格式上传。</p>
 *
 * <p>单段 / 多段双模式：
 * <ul>
 *   <li>单段 {@link ImportTask}：CSV 与 XLSX 皆可，逐行处理一个表；</li>
 *   <li>多段 {@link MultiSegmentImportTask}：仅 XLSX（多工作表原生能力），按工作表名匹配各
 *       {@link SegmentSpec} 并逐段独立校验 / 落库 / 统计，单段失败不影响其余段。</li>
 * </ul>
 * 导入中心按 {@code instanceof MultiSegmentImportTask} 自动识别并分支，既有单段模板无需改动。</p>
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
        return importDetailed(code, fileName, content, dryRun).result();
    }

    /**
     * 同 {@link #importData(String, String, byte[], boolean)}，额外返回失败行的
     * <b>原始单元格值</b>（{@link ImportOutcome#failedRows()}），供控制器生成
     * 「导入失败报告 CSV」——用户可离线修复后重传。常规导入请用 {@code importData}。
     */
    public ImportOutcome importDetailed(String code, String fileName, byte[] content, boolean dryRun) {
        ImportTask task = task(code);
        if (task instanceof MultiSegmentImportTask msTask) {
            return importMultiSegment(msTask, fileName, content, dryRun);
        }
        return importSingleSegment(task, fileName, content, dryRun);
    }

    /** 单段模板导入（CSV / XLSX 通用），逐行校验 / 落库，产出单段统计。 */
    private ImportOutcome importSingleSegment(ImportTask task, String fileName, byte[] content, boolean dryRun) {
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
        validateHeaders(task.displayName(), task.headers(), data.headers());

        int maxRows = importProperties.getMaxRows();
        if (data.rowCount() > maxRows) {
            throw BusinessException.badRequest(
                    "导入行数(" + data.rowCount() + ")超出上限(" + maxRows + ")，请拆分文件后分批导入");
        }

        List<String> taskHeaders = task.headers();
        List<RowError> errors = new ArrayList<>();
        List<FailedRow> failedRows = new ArrayList<>();
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
                String message = String.join("；", rowErrors);
                errors.add(new RowError(rowNo, message));
                failedRows.add(new FailedRow(task.displayName(), rowNo, message, values));
            } else {
                log.warn("导入 {} 第 {} 行失败（错误明细已达上限，仅计数）: {}", task.code(), rowNo, rowErrors);
            }
        }
        log.info("导入完成：模板={}，格式={}，预检={}，处理 {} 行，成功 {} 行，失败 {} 行",
                task.code(), format, dryRun, processed, success, failed);
        SegmentResult seg = new SegmentResult(task.displayName(), processed, success, failed);
        return new ImportOutcome(new ImportResult(dryRun, processed, success, failed, errors, List.of(seg)), failedRows);
    }

    /**
     * 多段模板导入（仅 XLSX）：按工作表名匹配各 {@link SegmentSpec}，逐段独立校验 / 落库 / 统计。
     * 单段失败不影响其余段；缺失工作表或表头不符即整体拒绝。
     */
    private ImportOutcome importMultiSegment(MultiSegmentImportTask task, String fileName, byte[] content,
                                            boolean dryRun) {
        ImportFormat format = ImportFormat.detect(fileName);
        if (format == null) {
            throw BusinessException.badRequest("仅支持 CSV / XLSX 文件，当前文件：" + fileName);
        }
        if (format != ImportFormat.XLSX) {
            throw BusinessException.badRequest("多段导入模板仅支持 XLSX（含多工作表），当前文件：" + fileName);
        }
        if (content == null || content.length == 0) {
            throw BusinessException.badRequest("上传文件内容为空");
        }

        List<Segment> segments;
        try {
            segments = XlsxDataReader.readWorkbook(content);
        } catch (IllegalArgumentException e) {
            // 解析类异常统一转为 400，避免以 500 暴露内部堆栈
            throw BusinessException.badRequest(e.getMessage());
        }
        if (segments.isEmpty()) {
            throw BusinessException.badRequest("文件无有效内容（至少需一个工作表）");
        }

        // 段名（忽略大小写与首尾空白）→ 段；便于用户以任意大小写命名工作表
        Map<String, Segment> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Segment s : segments) {
            byName.put(s.name().trim(), s);
        }

        int totalRows = segments.stream().mapToInt(Segment::rowCount).sum();
        int maxRows = importProperties.getMaxRows();
        if (totalRows > maxRows) {
            throw BusinessException.badRequest(
                    "导入总行数(" + totalRows + ")超出上限(" + maxRows + ")，请拆分文件后分批导入");
        }

        List<RowError> errors = new ArrayList<>();
        List<FailedRow> failedRows = new ArrayList<>();
        int processed = 0;
        List<SegmentResult> segResults = new ArrayList<>();

        for (SegmentSpec spec : task.segments()) {
            Segment seg = byName.get(spec.name().trim());
            if (seg == null) {
                throw BusinessException.badRequest("缺少工作表「" + spec.name() + "」：文件中未找到该工作表");
            }
            TabularData data = seg.data();
            validateHeaders(spec.name(), spec.headers(), data.headers());

            int segTotal = 0;
            int segOk = 0;
            List<String> specHeaders = spec.headers();
            for (int i = 0; i < data.rows().size(); i++) {
                int rowNo = i + 1;
                Map<String, String> values = toValues(specHeaders, data.rows().get(i));
                if (isBlankRow(values)) {
                    continue;
                }
                segTotal++;
                processed++;
                ImportRow row = new ImportRow(rowNo, values);
                List<String> rowErrors = new ArrayList<>(spec.validate(row));
                if (rowErrors.isEmpty()) {
                    if (dryRun) {
                        segOk++;
                    } else if (applySpec(spec, row, rowErrors)) {
                        segOk++;
                    }
                }
                if (rowErrors.isEmpty()) {
                    continue;
                }
                if (errors.size() < importProperties.getMaxErrors()) {
                    String message = String.join("；", rowErrors);
                    errors.add(new RowError(rowNo, message));
                    failedRows.add(new FailedRow(spec.name(), rowNo, message, values));
                } else {
                    log.warn("导入 {} 段「{}」第 {} 行失败（错误明细已达上限，仅计数）: {}",
                            task.code(), spec.name(), rowNo, rowErrors);
                }
            }
            segResults.add(new SegmentResult(spec.name(), segTotal, segOk, segTotal - segOk));
        }

        int success = segResults.stream().mapToInt(SegmentResult::successCount).sum();
        int failed = segResults.stream().mapToInt(SegmentResult::failureCount).sum();
        log.info("多段导入完成：模板={}，预检={}，处理 {} 行，成功 {} 行，失败 {} 行，段数 {}",
                task.code(), dryRun, processed, success, failed, segResults.size());
        return new ImportOutcome(new ImportResult(dryRun, processed, success, failed, errors, List.copyOf(segResults)),
                failedRows);
    }

    /** 落库单行（单段）；异常转为该行错误信息（不中断其余行）。 */
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

    /** 落库单行（多段下的某段）；异常转为该行错误信息（不中断其余行 / 段）。 */
    private boolean applySpec(SegmentSpec spec, ImportRow row, List<String> rowErrors) {
        try {
            spec.importRow(row);
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
    private void validateHeaders(String label, List<String> expected, List<String> actual) {
        if (actual.size() != expected.size()) {
            throw BusinessException.badRequest("工作表「" + label + "」表头列数不匹配：模板需 " + expected.size() + " 列（"
                    + String.join(" / ", expected) + "），文件为 " + actual.size() + " 列");
        }
        for (int c = 0; c < expected.size(); c++) {
            String a = actual.get(c) == null ? "" : actual.get(c).trim();
            if (!expected.get(c).equals(a)) {
                throw BusinessException.badRequest("工作表「" + label + "」第 " + (c + 1) + " 列表头不匹配：模板为「"
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
