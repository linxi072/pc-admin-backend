package com.acme.scaffold.system.export;

import com.acme.scaffold.system.dto.OrgTreeVO;
import com.acme.scaffold.system.service.OrgService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DeptExportTask} 单元测试：树形部门导出为扁平表的顺序、上级回显与顶级占位。
 *
 * <p>重点锁定：深度优先「父先于子」顺序（用户对照表格逐级核对）、上级部门名称映射、
 * 顶级与被过滤父节点显「（顶级）」、负责人缺失为空串。
 */
class DeptExportTaskTest {

    /** ACME(1) → [信息技术部(2) → 研发组(4), 市场部(3)]，同级按 sort 次序。 */
    private static List<OrgTreeVO> sampleTree() {
        OrgTreeVO rnd = new OrgTreeVO(4L, 2L, "ACME_RND", "研发组", "DEPARTMENT", 1, "ACTIVE", null, null, List.of());
        OrgTreeVO it = new OrgTreeVO(2L, 1L, "ACME_IT", "信息技术部", "DEPARTMENT", 1, "ACTIVE", 100L, "张三", List.of(rnd));
        OrgTreeVO mkt = new OrgTreeVO(3L, 1L, "ACME_MKT", "市场部", "DEPARTMENT", 2, "ACTIVE", 101L, "李四", List.of());
        OrgTreeVO root = new OrgTreeVO(1L, 0L, "ACME", "ACME 集团", "COMPANY", 1, "ACTIVE", null, null, List.of(it, mkt));
        return List.of(root);
    }

    @Test
    void declaresExpectedMetadata() {
        DeptExportTask task = new DeptExportTask(mock(OrgService.class));
        assertEquals("depts", task.code());
        assertEquals("部门列表", task.displayName());
        assertEquals("dept", task.fileName());
        assertFalse(task.headers().isEmpty());
    }

    @Test
    void flattensDepthFirstParentBeforeChild() {
        OrgService svc = mock(OrgService.class);
        when(svc.tree()).thenReturn(sampleTree());
        DeptExportTask task = new DeptExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals(4, rows.size());
        // 深度优先保序：ACME → 信息技术部 → 研发组 → 市场部
        assertEquals("ACME", rows.get(0).get(1));
        assertEquals("ACME_IT", rows.get(1).get(1));
        assertEquals("ACME_RND", rows.get(2).get(1));
        assertEquals("ACME_MKT", rows.get(3).get(1));
        for (List<String> row : rows) {
            assertEquals(task.headers().size(), row.size(), "行宽必须与表头一致");
        }
    }

    @Test
    void resolvesParentNameAndTopLevelLabel() {
        OrgService svc = mock(OrgService.class);
        when(svc.tree()).thenReturn(sampleTree());
        DeptExportTask task = new DeptExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals("（顶级）", rows.get(0).get(3), "顶级部门显占位");
        assertEquals("ACME 集团", rows.get(1).get(3), "上级部门按名称回显");
        assertEquals("信息技术部", rows.get(2).get(3));
        assertEquals("ACME 集团", rows.get(3).get(3));
    }

    @Test
    void rendersLeaderNameAndBlankFallback() {
        OrgService svc = mock(OrgService.class);
        when(svc.tree()).thenReturn(sampleTree());
        DeptExportTask task = new DeptExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals("", rows.get(0).get(4), "顶级无负责人为空串");
        assertEquals("张三", rows.get(1).get(4));
        assertEquals("", rows.get(2).get(4), "研发组无负责人为空串");
        assertEquals("李四", rows.get(3).get(4));
    }

    @Test
    void orphanedParentRendersAsTopLevel() {
        OrgService svc = mock(OrgService.class);
        // 数据权限过滤掉父节点时该行升为顶级（父不在映射内）——与 OrgService.tree 的森林语义一致
        OrgTreeVO orphan = new OrgTreeVO(5L, 999L, "ORPHAN", "孤儿子部门", "DEPARTMENT", 1, "ACTIVE", null, null, List.of());
        when(svc.tree()).thenReturn(List.of(orphan));
        DeptExportTask task = new DeptExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals("（顶级）", rows.get(0).get(3), "父节点不可见时按顶级处理");
    }

    @Test
    void emptyTreeYieldsNoRows() {
        OrgService svc = mock(OrgService.class);
        when(svc.tree()).thenReturn(List.of());
        DeptExportTask task = new DeptExportTask(svc);

        assertEquals(0, task.rows(Map.of()).size());
    }
}
