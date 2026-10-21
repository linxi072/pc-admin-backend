package com.acme.scaffold.system.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.export.XlsxDataExporter;
import com.acme.scaffold.common.importer.ImportOutcome;
import com.acme.scaffold.common.importer.ImportResult;
import com.acme.scaffold.common.importer.ImportRow;
import com.acme.scaffold.common.importer.ImportTask;
import com.acme.scaffold.common.importer.ImportValidators;
import com.acme.scaffold.system.importer.config.ImportProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导入中心运行时测试。
 *
 * <p>用假任务（不触库）验证与具体业务无关的核心语义：
 * 格式识别、表头校验、行号定位、空行跳过、逐行错误收集、预检不落库、
 * 业务冲突转行错误、行数上限、错误条数上限，以及 CSV / XLSX 双格式等价。</p>
 */
class ImportCenterServiceTest {

    private static final String HEADER_NAME = "名称";
    private static final String HEADER_COUNT = "数量";

    /** 假导入任务：名称必填、数量为 1~999 整数；名称为 dup 时模拟业务冲突。 */
    private static final class FakeTask implements ImportTask {

        private final List<String> imported = new ArrayList<>();

        @Override
        public String code() {
            return "demo";
        }

        @Override
        public String displayName() {
            return "演示数据";
        }

        @Override
        public List<String> headers() {
            return List.of(HEADER_NAME, HEADER_COUNT);
        }

        @Override
        public List<String> validate(ImportRow row) {
            return ImportValidators.collect(
                    ImportValidators.required(row.trimmed(HEADER_NAME), HEADER_NAME),
                    ImportValidators.integerRange(row.trimmed(HEADER_COUNT), HEADER_COUNT, 1, 999, false));
        }

        @Override
        public void importRow(ImportRow row) {
            if ("dup".equals(row.trimmed(HEADER_NAME))) {
                throw BusinessException.conflict("名称重复");
            }
            imported.add(row.trimmed(HEADER_NAME));
        }
    }

    private static ImportCenterService service(ImportProperties props, ImportTask... tasks) {
        return new ImportCenterService(List.of(tasks), props);
    }

    private static byte[] csv(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void dryRunValidatesButDoesNotPersist() {
        FakeTask task = new FakeTask();
        ImportResult result = service(new ImportProperties(), task)
                .importData("demo", "demo.csv", csv("名称,数量\nA,1\n"), true);

        assertTrue(result.dryRun());
        assertEquals(1, result.totalRows());
        assertEquals(1, result.successCount());
        assertEquals(0, result.failureCount());
        assertTrue(task.imported.isEmpty(), "预检模式不得落库");
    }

    @Test
    void importsValidRowsAndSkipsBlankOnes() {
        FakeTask task = new FakeTask();
        ImportResult result = service(new ImportProperties(), task)
                .importData("demo", "demo.csv", csv("名称,数量\nA,1\n\nB,2\n"), false);

        assertEquals(2, result.totalRows(), "全空行不计入处理行数");
        assertEquals(2, result.successCount());
        assertTrue(result.allSucceeded());
        assertEquals(List.of("A", "B"), task.imported);
    }

    @Test
    void reportsRowErrorsWithFileRowNumbers() {
        FakeTask task = new FakeTask();
        ImportResult result = service(new ImportProperties(), task)
                .importData("demo", "demo.csv", csv("名称,数量\n,1\nB,x\nC,3\n"), false);

        assertEquals(3, result.totalRows());
        assertEquals(1, result.successCount());
        assertEquals(2, result.failureCount());
        // 行号从 1 起（不含表头）：文件第 1 数据行缺名称、第 2 数据行数量非整数
        assertEquals(1, result.errors().get(0).rowNo());
        assertTrue(result.errors().get(0).message().contains(HEADER_NAME));
        assertEquals(2, result.errors().get(1).rowNo());
        assertTrue(result.errors().get(1).message().contains(HEADER_COUNT));
        // 单行失败不影响其余行
        assertEquals(List.of("C"), task.imported);
    }

    @Test
    void businessConflictIsCollectedAsRowError() {
        FakeTask task = new FakeTask();
        ImportResult result = service(new ImportProperties(), task)
                .importData("demo", "demo.csv", csv("名称,数量\ndup,1\n"), false);

        assertEquals(1, result.failureCount());
        assertTrue(result.errors().get(0).message().contains("名称重复"));
        assertTrue(task.imported.isEmpty());
    }

    @Test
    void xlsxIsParsedEquallyToCsv() {
        FakeTask task = new FakeTask();
        List<String> headers = List.of(HEADER_NAME, HEADER_COUNT);
        List<List<String>> rows = List.of(List.of("A", "1"), List.of("B", "2"));
        byte[] xlsx = XlsxDataExporter.export(headers, rows);

        ImportResult result = service(new ImportProperties(), task)
                .importData("demo", "demo.xlsx", xlsx, false);

        assertEquals(2, result.successCount());
        assertEquals(List.of("A", "B"), task.imported);
    }

    @Test
    void rejectsHeaderMismatch() {
        ImportCenterService service = service(new ImportProperties(), new FakeTask());
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.importData("demo", "demo.csv", csv("名称,数量X\nA,1\n"), false));
        assertTrue(e.getMessage().contains("表头"), e.getMessage());

        BusinessException sizeError = assertThrows(BusinessException.class,
                () -> service.importData("demo", "demo.csv", csv("名称\nA\n"), false));
        assertTrue(sizeError.getMessage().contains("列数不匹配"), sizeError.getMessage());
    }

    @Test
    void rejectsUnsupportedExtensionAndEmptyContent() {
        ImportCenterService service = service(new ImportProperties(), new FakeTask());
        assertThrows(BusinessException.class,
                () -> service.importData("demo", "demo.txt", csv("名称,数量\nA,1\n"), false));
        assertThrows(BusinessException.class,
                () -> service.importData("demo", "demo.csv", new byte[0], false));
        assertThrows(BusinessException.class,
                () -> service.importData("demo", null, csv("名称,数量\nA,1\n"), false));
    }

    @Test
    void rejectsUnknownTemplateAndReportsNotFound() {
        ImportCenterService service = service(new ImportProperties(), new FakeTask());
        BusinessException e = assertThrows(BusinessException.class, () -> service.task("nope"));
        assertEquals("COMMON_404", e.getErrorCode().code());
        assertEquals(1, service.tasks().size());
        assertEquals("demo", service.task("demo").code());
    }

    @Test
    void rejectsRowCountExceedingLimit() {
        ImportProperties props = new ImportProperties();
        props.setMaxRows(1);
        ImportCenterService service = service(props, new FakeTask());
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.importData("demo", "demo.csv", csv("名称,数量\nA,1\nB,2\n"), false));
        assertTrue(e.getMessage().contains("超出上限"), e.getMessage());
    }

    @Test
    void capsReturnedErrorDetailsButStillCountsAll() {
        ImportProperties props = new ImportProperties();
        props.setMaxErrors(1);
        ImportResult result = service(props, new FakeTask())
                .importData("demo", "demo.csv", csv("名称,数量\n,1\n,2\n,3\n"), false);

        assertEquals(3, result.totalRows());
        assertEquals(0, result.successCount());
        // 失败行数为真实值（3），明细回传受上限约束（1）——两者刻意分离
        assertEquals(3, result.failureCount());
        assertEquals(1, result.errors().size());
    }

    @Test
    void importPropertiesRejectNonPositiveLimits() {
        ImportProperties props = new ImportProperties();
        props.setMaxRows(0);
        props.setMaxErrors(-1);
        assertEquals(1, props.getMaxRows());
        assertEquals(1, props.getMaxErrors());
    }

    @Test
    void detailedOutcomeCarriesOriginalRowValuesForFailedRows() {
        ImportOutcome outcome = service(new ImportProperties(), new FakeTask())
                .importDetailed("demo", "demo.csv", csv("名称,数量\n,1\nB,x\nC,3\n"), false);

        // 失败行保留原始单元格值，供「失败报告 CSV」还原行内容
        assertEquals(2, outcome.failedRows().size());
        assertEquals("", outcome.failedRows().get(0).values().get(HEADER_NAME));
        assertEquals("1", outcome.failedRows().get(0).values().get(HEADER_COUNT));
        assertEquals("B", outcome.failedRows().get(1).values().get(HEADER_NAME));
        assertEquals("x", outcome.failedRows().get(1).values().get(HEADER_COUNT));
        // 与 result 同口径：同序、同范围
        assertEquals(outcome.result().errors().size(), outcome.failedRows().size());
        assertEquals(1, outcome.result().successCount());
    }

    @Test
    void detailedOutcomeHasNoFailedRowsWhenAllSucceed() {
        ImportOutcome outcome = service(new ImportProperties(), new FakeTask())
                .importDetailed("demo", "demo.csv", csv("名称,数量\nA,1\n"), true);

        assertTrue(outcome.failedRows().isEmpty());
        assertEquals(1, outcome.result().successCount());
        assertTrue(outcome.result().allSucceeded());
    }
}
