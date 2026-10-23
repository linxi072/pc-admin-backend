package com.acme.scaffold.system.importer;

import com.acme.scaffold.common.importer.ImportRow;
import com.acme.scaffold.system.dto.CreateDictDataRequest;
import com.acme.scaffold.system.dto.CreateDictTypeRequest;
import com.acme.scaffold.system.service.DictDataService;
import com.acme.scaffold.system.service.DictTypeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 字典完整包多段导入试点测试（纯校验 + 落库映射，不触库）。
 *
 * <p>覆盖：两段声明、各段表头、各段校验规则、各段落库委托到正确的服务（互不串段），
 * 以及可选项留空归一为 null。</p>
 */
class DictFullImportTaskTest {

    private static ImportRow typeRow(String code, String name, String status, String sort, String remark) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("字典编码", code);
        m.put("字典名称", name);
        m.put("状态", status);
        m.put("排序", sort);
        m.put("备注", remark);
        return new ImportRow(1, m);
    }

    private static ImportRow dataRow(String type, String label, String value, String sort, String status, String remark) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("字典类型", type);
        m.put("字典标签", label);
        m.put("字典值", value);
        m.put("排序", sort);
        m.put("状态", status);
        m.put("备注", remark);
        return new ImportRow(1, m);
    }

    @Test
    void exposesTwoSegmentsInOrder() {
        DictFullImportTask task = new DictFullImportTask(mock(DictTypeService.class), mock(DictDataService.class));
        assertEquals("dict-full", task.code());
        assertEquals(2, task.segments().size());
        assertEquals("字典类型", task.segments().get(0).name());
        assertEquals("字典数据", task.segments().get(1).name());
        assertEquals(List.of("字典编码", "字典名称", "状态", "排序", "备注"), task.segments().get(0).headers());
    }

    @Test
    void typeSegmentValidatesAndImports() {
        DictTypeService typeSvc = mock(DictTypeService.class);
        DictDataService dataSvc = mock(DictDataService.class);
        DictFullImportTask task = new DictFullImportTask(typeSvc, dataSvc);

        assertTrue(task.segments().get(0).validate(typeRow("gender", "性别", "ACTIVE", "1", "")).isEmpty());

        task.segments().get(0).importRow(typeRow(" gender ", " 性别 ", " active ", " 2 ", " 备注 "));
        ArgumentCaptor<CreateDictTypeRequest> captor = ArgumentCaptor.forClass(CreateDictTypeRequest.class);
        verify(typeSvc).create(captor.capture());
        CreateDictTypeRequest req = captor.getValue();
        assertEquals("gender", req.dictCode());
        assertEquals("性别", req.dictName());
        assertEquals("active", req.status());
        assertEquals(2, req.sortNo());
        assertEquals("备注", req.remark());
        // 类型段落库不触碰数据服务
        verify(dataSvc, never()).create(any());
    }

    @Test
    void dataSegmentValidatesAndImports() {
        DictTypeService typeSvc = mock(DictTypeService.class);
        DictDataService dataSvc = mock(DictDataService.class);
        DictFullImportTask task = new DictFullImportTask(typeSvc, dataSvc);

        assertTrue(task.segments().get(1).validate(dataRow("gender", "男", "1", "0", "ACTIVE", "")).isEmpty());

        task.segments().get(1).importRow(dataRow(" gender ", " 男 ", " 1 ", "", "", ""));
        ArgumentCaptor<CreateDictDataRequest> captor = ArgumentCaptor.forClass(CreateDictDataRequest.class);
        verify(dataSvc).create(captor.capture());
        CreateDictDataRequest req = captor.getValue();
        assertEquals("gender", req.dictTypeCode());
        assertEquals("男", req.dictLabel());
        assertEquals("1", req.dictValue());
        // 排序 / 状态 / 备注留空归一为 null，交由服务层取默认
        assertEquals(null, req.dictSort());
        assertEquals(null, req.status());
        assertEquals(null, req.remark());
        verify(typeSvc, never()).create(any());
    }

    @Test
    void typeSegmentCollectsAllFieldErrors() {
        DictFullImportTask task = new DictFullImportTask(mock(DictTypeService.class), mock(DictDataService.class));
        List<String> errs = task.segments().get(0).validate(typeRow("", "", "BAD", "x", "y".repeat(300)));
        assertTrue(errs.stream().anyMatch(e -> e.contains("字典编码")));
        assertTrue(errs.stream().anyMatch(e -> e.contains("字典名称")));
        assertTrue(errs.stream().anyMatch(e -> e.contains("状态")));
        assertTrue(errs.stream().anyMatch(e -> e.contains("排序")));
        assertTrue(errs.stream().anyMatch(e -> e.contains("备注")));
    }
}
