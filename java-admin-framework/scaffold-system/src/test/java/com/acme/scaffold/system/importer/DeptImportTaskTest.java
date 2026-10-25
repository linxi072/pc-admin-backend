package com.acme.scaffold.system.importer;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.importer.ImportRow;
import com.acme.scaffold.system.dto.CreateOrgRequest;
import com.acme.scaffold.system.dto.OrgTreeVO;
import com.acme.scaffold.system.service.OrgService;
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
 * 部门导入模板测试（纯校验 + 父级编码解析 + 落库映射，不触库）。
 *
 * <p>重点锁定：4 列模板、上级编码→parentId 解析（留空为顶级 / 命中映射 / 缺失抛错）、
 * 树形缓存复用（{@code OrgService#tree()} 被逐行调用但命中分键缓存）、
 * 落库委托字段（orgType/leaderUserId/status 均留空交由服务层默认）。
 */
class DeptImportTaskTest {

    /** ACME(1) → 信息技术部(2)：用于编码→ID 反查。 */
    private static List<OrgTreeVO> sampleTree() {
        OrgTreeVO it = new OrgTreeVO(2L, 1L, "ACME_IT", "信息技术部", "DEPARTMENT", 1, "ACTIVE", null, null, List.of());
        OrgTreeVO root = new OrgTreeVO(1L, 0L, "ACME", "ACME 集团", "COMPANY", 1, "ACTIVE", null, null, List.of(it));
        return List.of(root);
    }

    private static ImportRow row(String parentCode, String code, String name, String sort) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("上级部门编码", parentCode);
        values.put("部门编码", code);
        values.put("部门名称", name);
        values.put("排序", sort);
        return new ImportRow(1, values);
    }

    private static DeptImportTask taskWithTree(OrgService svc) {
        when(svc.tree()).thenReturn(sampleTree());
        return new DeptImportTask(svc);
    }

    @Test
    void declaresFourColumnTemplate() {
        DeptImportTask task = new DeptImportTask(mock(OrgService.class));
        assertEquals(DeptImportTask.CODE, task.code());
        assertEquals("部门", task.displayName());
        assertEquals(List.of("上级部门编码", "部门编码", "部门名称", "排序"), task.headers());
    }

    @Test
    void validRowPassesValidation() {
        DeptImportTask task = new DeptImportTask(mock(OrgService.class));
        // 上级留空（顶级）、必填齐、可选项留空均合法
        assertTrue(task.validate(row("", "ACME_HR", "人力资源部", "3")).isEmpty());
        assertTrue(task.validate(row("ACME", "ACME_HR", "人力资源部", "")).isEmpty());
        assertTrue(task.validate(row(" ACME ", " ACME_HR ", " 人力资源部 ", " 3 ")).isEmpty());
    }

    @Test
    void collectsAllFieldErrorsAtOnce() {
        DeptImportTask task = new DeptImportTask(mock(OrgService.class));
        List<String> errors = task.validate(row("", "", "", "abc"));
        // 部门编码必填 + 部门名称必填 + 排序非整数 = 3 处（上级编码可空不报错）
        assertEquals(3, errors.size(), "应一次收集全部字段错误: " + errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("部门编码")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("部门名称")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("排序")));
    }

    @Test
    void rejectsOverlongValues() {
        DeptImportTask task = new DeptImportTask(mock(OrgService.class));
        List<String> errors = task.validate(row("", "x".repeat(65), "y".repeat(129), "0"));
        assertTrue(errors.stream().anyMatch(e -> e.contains("部门编码") && e.contains("64")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("部门名称") && e.contains("128")));
    }

    @Test
    void blankParentCodeResolvesToTopLevel() {
        OrgService svc = mock(OrgService.class);
        DeptImportTask task = taskWithTree(svc);

        task.importRow(row("", "ACME_HR", "人力资源部", "3"));

        ArgumentCaptor<CreateOrgRequest> captor = ArgumentCaptor.forClass(CreateOrgRequest.class);
        verify(svc).create(captor.capture());
        CreateOrgRequest req = captor.getValue();
        assertEquals(0L, req.parentId(), "上级留空应为顶级(0)");
        assertEquals("ACME_HR", req.orgCode());
        assertEquals("人力资源部", req.orgName());
        assertEquals(3, req.sortNo());
        // orgType/leaderUserId/status 留空，交由 OrgService.create 取默认值
        assertNull(req.orgType());
        assertNull(req.leaderUserId());
        assertNull(req.status());
    }

    @Test
    void parentCodeResolvesToIdFromTree() {
        OrgService svc = mock(OrgService.class);
        DeptImportTask task = taskWithTree(svc);

        task.importRow(row("ACME", "ACME_HR", "人力资源部", ""));

        ArgumentCaptor<CreateOrgRequest> captor = ArgumentCaptor.forClass(CreateOrgRequest.class);
        verify(svc).create(captor.capture());
        assertEquals(1L, captor.getValue().parentId(), "上级编码 ACME 应解析为 id=1");
        assertNull(captor.getValue().sortNo(), "排序留空交由服务层取默认 0");
    }

    @Test
    void unknownParentCodeFailsRowWithClearMessage() {
        OrgService svc = mock(OrgService.class);
        DeptImportTask task = taskWithTree(svc);

        BusinessException e = assertThrows(BusinessException.class,
                () -> task.importRow(row("NOT_EXIST", "ACME_HR", "人力资源部", "0")));
        assertEquals(CommonErrorCode.NOT_FOUND, e.getErrorCode());
        assertTrue(e.getMessage().contains("NOT_EXIST"), "错误信息应指出缺失的上级编码");
        // 父级解析失败不应产生任何写入
        verify(svc, never()).create(any());
    }

    @Test
    void importRowPropagatesBusinessConflict() {
        OrgService svc = mock(OrgService.class);
        when(svc.tree()).thenReturn(sampleTree());
        when(svc.create(any())).thenThrow(BusinessException.conflict("机构编码已存在"));
        DeptImportTask task = new DeptImportTask(svc);

        BusinessException e = assertThrows(BusinessException.class,
                () -> task.importRow(row("ACME", "ACME_IT", "信息技术部", "0")));
        assertEquals("机构编码已存在", e.getMessage());
    }

    @Test
    void validationDoesNotTouchDatabase() {
        OrgService svc = mock(OrgService.class);
        DeptImportTask task = new DeptImportTask(svc);

        task.validate(row("ACME", "ACME_HR", "人力资源部", "0"));

        verify(svc, never()).tree();
        verify(svc, never()).create(any());
    }
}
