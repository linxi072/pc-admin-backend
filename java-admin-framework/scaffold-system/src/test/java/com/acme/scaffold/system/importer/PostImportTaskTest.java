package com.acme.scaffold.system.importer;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.importer.ImportRow;
import com.acme.scaffold.system.dto.CreatePostRequest;
import com.acme.scaffold.system.service.PostService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 岗位导入模板测试（纯校验 + 落库映射，不触库）。
 *
 * <p>重点锁定：4 列模板（无状态列）、编码形态校验、一次收集全部字段错误、
 * 排序/备注可选项的 trim 与 null 语义、库层冲突向上传播（由导入中心计为该行失败）。
 */
class PostImportTaskTest {

    private static ImportRow row(String code, String name, String sort, String remark) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("岗位编码", code);
        values.put("岗位名称", name);
        values.put("排序", sort);
        values.put("备注", remark);
        return new ImportRow(1, values);
    }

    @Test
    void declaresFourColumnTemplateWithoutStatus() {
        PostImportTask task = new PostImportTask(mock(PostService.class));
        assertEquals(PostImportTask.CODE, task.code());
        assertEquals("岗位", task.displayName());
        // 不含状态列：create 固定 ACTIVE，模板放状态列只会误导
        assertEquals(List.of("岗位编码", "岗位名称", "排序", "备注"), task.headers());
    }

    @Test
    void validRowPassesValidation() {
        PostImportTask task = new PostImportTask(mock(PostService.class));
        assertTrue(task.validate(row("gm-001", "总经理", "1", "公司负责人")).isEmpty());
        // 可选项留空亦通过
        assertTrue(task.validate(row("gm-001", "总经理", "", "")).isEmpty());
        // 允许首尾空白（落库前会 trim）
        assertTrue(task.validate(row(" gm-001 ", " 总经理 ", " 1 ", " x ")).isEmpty());
    }

    @Test
    void collectsAllFieldErrorsAtOnce() {
        PostImportTask task = new PostImportTask(mock(PostService.class));
        List<String> errors = task.validate(row("", "", "abc", ""));
        // 编码必填 + 名称必填 + 排序非整数 = 3 处，应一次全部回传
        assertEquals(3, errors.size(), "应一次收集全部字段错误: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("岗位编码")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("岗位名称")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("排序")));
    }

    @Test
    void rejectsInvalidCodeFormat() {
        PostImportTask task = new PostImportTask(mock(PostService.class));
        // 与 PostService.create 同一形态规则（字母/数字/下划线/连字符）
        List<String> errors = task.validate(row("岗位 001", "总经理", "1", ""));
        assertTrue(errors.stream().anyMatch(e -> e.contains("岗位编码") && e.contains("字母")),
                "含空白的编码应报形态错误: " + errors);
    }

    @Test
    void rejectsOverlongValues() {
        PostImportTask task = new PostImportTask(mock(PostService.class));
        List<String> errors = task.validate(row("x".repeat(65), "名称", "0", ""));
        assertTrue(errors.stream().anyMatch(e -> e.contains("岗位编码") && e.contains("64")));

        List<String> remarkErrors = task.validate(row("gm", "名称", "0", "y".repeat(513)));
        assertTrue(remarkErrors.stream().anyMatch(e -> e.contains("备注") && e.contains("512")));
    }

    @Test
    void importRowTrimsAndNullsBlankOptionals() {
        PostService service = mock(PostService.class);
        PostImportTask task = new PostImportTask(service);

        task.importRow(row(" gm-001 ", " 总经理 ", "", "  "));

        ArgumentCaptor<CreatePostRequest> captor = ArgumentCaptor.forClass(CreatePostRequest.class);
        verify(service).create(captor.capture());
        CreatePostRequest req = captor.getValue();
        assertEquals("gm-001", req.postCode());
        assertEquals("总经理", req.postName());
        // 排序留空交由服务层取默认 0；备注留空存 null
        assertNull(req.postSort());
        assertNull(req.remark());
    }

    @Test
    void importRowKeepsProvidedOptionals() {
        PostService service = mock(PostService.class);
        PostImportTask task = new PostImportTask(service);

        task.importRow(row("gm-001", "总经理", " 5 ", " 说明 "));

        ArgumentCaptor<CreatePostRequest> captor = ArgumentCaptor.forClass(CreatePostRequest.class);
        verify(service).create(captor.capture());
        CreatePostRequest req = captor.getValue();
        assertEquals(5, req.postSort());
        assertEquals("说明", req.remark());
    }

    @Test
    void importRowPropagatesBusinessConflict() {
        PostService service = mock(PostService.class);
        when(service.create(any())).thenThrow(BusinessException.conflict("岗位编码已存在"));
        PostImportTask task = new PostImportTask(service);

        BusinessException e = assertThrows(BusinessException.class,
                () -> task.importRow(row("gm-001", "总经理", "0", "")));
        assertEquals("岗位编码已存在", e.getMessage());
    }

    @Test
    void validationDoesNotTouchDatabase() {
        PostService service = mock(PostService.class);
        PostImportTask task = new PostImportTask(service);

        task.validate(row("gm-001", "总经理", "0", ""));

        verify(service, never()).create(any());
        verify(service, never()).list(any());
    }
}
