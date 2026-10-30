package com.acme.scaffold.system.export;

import com.acme.scaffold.system.dto.RoleView;
import com.acme.scaffold.system.service.RoleService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RoleExportTask} 单元测试：模板契约与行映射。
 *
 * <p>角色为百级小数据集，{@link RoleService#list()} 一次全量返回，本类重点锁定
 * 表头/行宽一致与超上限截断护栏。
 */
class RoleExportTaskTest {

    private static RoleView sample() {
        return new RoleView(10L, "ROLE_ADMIN", "超级管理员", "SYSTEM", "ACTIVE", 1, Set.of(), Set.of());
    }

    @Test
    void declaresExpectedMetadata() {
        RoleExportTask task = new RoleExportTask(mock(RoleService.class));
        assertEquals("roles", task.code());
        assertEquals("角色列表", task.displayName());
        assertEquals("role", task.fileName());
        assertFalse(task.headers().isEmpty());
    }

    @Test
    void mapsRoleViewToRow() {
        RoleService svc = mock(RoleService.class);
        when(svc.list()).thenReturn(List.of(sample()));
        RoleExportTask task = new RoleExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals(1, rows.size());
        assertEquals(task.headers().size(), rows.get(0).size(), "行宽必须与表头一致");
        assertEquals("10", rows.get(0).get(0));
        assertEquals("ROLE_ADMIN", rows.get(0).get(1));
        assertEquals("超级管理员", rows.get(0).get(2));
        assertEquals("SYSTEM", rows.get(0).get(3));
        assertEquals("1", rows.get(0).get(4));
        assertEquals("ACTIVE", rows.get(0).get(5));
    }

    @Test
    void nullSortRendersAsBlank() {
        RoleService svc = mock(RoleService.class);
        when(svc.list()).thenReturn(List.of(new RoleView(11L, "R", "角色", "BUSINESS", "ACTIVE", null, Set.of(), Set.of())));
        RoleExportTask task = new RoleExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals("", rows.get(0).get(4), "null 排序应归一为空串");
    }

    @Test
    void truncatesAtMaxRows() {
        RoleService svc = mock(RoleService.class);
        when(svc.list()).thenReturn(java.util.stream.IntStream.range(0, 10_001)
                .mapToObj(i -> new RoleView((long) i, "R" + i, "角色" + i, "BUSINESS", "ACTIVE", 0, Set.of(), Set.of()))
                .toList());
        RoleExportTask task = new RoleExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals(10_000, rows.size(), "超出 10000 行应截断");
    }

    @Test
    void emptyRoleListYieldsNoRows() {
        RoleService svc = mock(RoleService.class);
        when(svc.list()).thenReturn(List.of());
        RoleExportTask task = new RoleExportTask(svc);

        assertEquals(0, task.rows(Map.of()).size());
    }
}
