package com.acme.scaffold.system.importer;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.importer.ImportRow;
import com.acme.scaffold.system.dto.CreateDictDataRequest;
import com.acme.scaffold.system.service.DictDataService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 字典数据导入试点任务测试（纯校验 + 落库映射，不触库）。
 */
class DictDataImportTaskTest {

    private static ImportRow row(String type, String label, String value, String sort, String status,
                                 String remark) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("字典类型", type);
        values.put("字典标签", label);
        values.put("字典值", value);
        values.put("排序", sort);
        values.put("状态", status);
        values.put("备注", remark);
        return new ImportRow(1, values);
    }

    @Test
    void headersMatchTableColumns() {
        DictDataImportTask task = new DictDataImportTask(mock(DictDataService.class));
        assertEquals(DictDataImportTask.CODE, task.code());
        assertEquals("字典数据", task.displayName());
        assertEquals(List.of("字典类型", "字典标签", "字典值", "排序", "状态", "备注"), task.headers());
    }

    @Test
    void validRowPassesValidation() {
        DictDataImportTask task = new DictDataImportTask(mock(DictDataService.class));
        assertTrue(task.validate(row("user_status", "启用", "ACTIVE", "1", "ACTIVE", "备注")).isEmpty());
        // 可选项留空亦通过
        assertTrue(task.validate(row("user_status", "启用", "ACTIVE", "", "", "")).isEmpty());
        // 允许首尾空白（落库前会 trim）
        assertTrue(task.validate(row(" user_status ", " 启用 ", " ACTIVE ", " 1 ", " active ", " x ")).isEmpty());
    }

    @Test
    void collectsAllFieldErrorsAtOnce() {
        DictDataImportTask task = new DictDataImportTask(mock(DictDataService.class));
        List<String> errors = task.validate(row("", "", "", "abc", "DELETED", ""));
        // 类型 / 标签 / 值 必填缺失 + 排序非整数 + 状态非法 = 5 处，应一次全部回传（备注可空不报错）
        assertEquals(5, errors.size(), "应一次收集全部字段错误: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("字典类型")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("字典标签")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("字典值")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("排序")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("状态")));
    }

    @Test
    void rejectsOverlongValues() {
        DictDataImportTask task = new DictDataImportTask(mock(DictDataService.class));
        String tooLong = "x".repeat(65);
        List<String> errors = task.validate(row(tooLong, "标签", "值", "0", "ACTIVE", ""));
        assertTrue(errors.stream().anyMatch(e -> e.contains("字典类型") && e.contains("64")));

        List<String> remarkErrors = task.validate(row("t", "l", "v", "0", "ACTIVE", "y".repeat(256)));
        assertTrue(remarkErrors.stream().anyMatch(e -> e.contains("备注")));
    }

    @Test
    void importRowTrimsAndNullsBlankOptionals() {
        DictDataService service = mock(DictDataService.class);
        DictDataImportTask task = new DictDataImportTask(service);

        task.importRow(row(" user_status ", " 启用 ", " ACTIVE ", "", "", "  "));

        ArgumentCaptor<CreateDictDataRequest> captor = ArgumentCaptor.forClass(CreateDictDataRequest.class);
        verify(service).create(captor.capture());
        CreateDictDataRequest req = captor.getValue();
        assertEquals("user_status", req.dictTypeCode());
        assertEquals("启用", req.dictLabel());
        assertEquals("ACTIVE", req.dictValue());
        // 排序留空交由服务层取默认 0；状态留空交由服务层取默认 ACTIVE；备注留空存 null
        assertNull(req.dictSort());
        assertNull(req.status());
        assertNull(req.remark());
    }

    @Test
    void importRowKeepsProvidedOptionals() {
        DictDataService service = mock(DictDataService.class);
        DictDataImportTask task = new DictDataImportTask(service);

        task.importRow(row("t", "l", "v", " 5 ", "INACTIVE", " 说明 "));

        ArgumentCaptor<CreateDictDataRequest> captor = ArgumentCaptor.forClass(CreateDictDataRequest.class);
        verify(service).create(captor.capture());
        CreateDictDataRequest req = captor.getValue();
        assertNotNull(req.dictSort());
        assertEquals(5, req.dictSort());
        assertEquals("INACTIVE", req.status());
        assertEquals("说明", req.remark());
    }

    @Test
    void importRowPropagatesBusinessConflict() {
        DictDataService service = mock(DictDataService.class);
        when(service.create(any())).thenThrow(BusinessException.conflict("该字典类型下字典值已存在"));
        DictDataImportTask task = new DictDataImportTask(service);

        BusinessException e = assertThrows(BusinessException.class,
                () -> task.importRow(row("t", "l", "v", "0", "ACTIVE", "")));
        assertEquals("该字典类型下字典值已存在", e.getMessage());
    }

    @Test
    void validationDoesNotTouchDatabase() {
        DictDataService service = mock(DictDataService.class);
        DictDataImportTask task = new DictDataImportTask(service);

        task.validate(row("t", "l", "v", "0", "ACTIVE", ""));

        verify(service, never()).create(any());
        verify(service, never()).listByType(any());
    }
}
