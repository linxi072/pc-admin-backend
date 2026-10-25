package com.acme.scaffold.system.export;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.system.dto.UserQuery;
import com.acme.scaffold.system.dto.UserView;
import com.acme.scaffold.system.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserExportTask} 单元测试：模板契约、翻页取数、筛选透传与多值拼列。
 *
 * <p>重点锁定：表头与行宽一致（否则 CSV/XLSX 错列）、超上限截断、空页防御（不死循环）、
 * 筛选参数透传（与用户管理列表页同口径）。数据权限由 {@code UserService#list} 上的切面负责，
 * 本类只验证透传，故直接构造视图数据。
 */
class UserExportTaskTest {

    private static UserView sampleView() {
        return UserView.from(1001L, "admin", "系统管理员", "22644212509", "admin@example.com",
                List.of(10L), List.of("超级管理员"), List.of("ROLE_ADMIN"),
                List.of(20L), List.of("ACME 集团"), List.of(30L), List.of("总经理", "技术总监"),
                "ACTIVE", LocalDateTime.of(2026, 10, 20, 8, 30));
    }

    private static PageResult<UserView> page(int pageNo, int size, long total, List<UserView> records) {
        return new PageResult<>(pageNo, size, total, records);
    }

    @Test
    void declaresExpectedMetadata() {
        UserExportTask task = new UserExportTask(mock(UserService.class));
        assertEquals("users", task.code());
        assertEquals("用户列表", task.displayName());
        assertEquals("user", task.fileName());
        assertFalse(task.headers().isEmpty());
    }

    @Test
    void headerAndRowWidthsAreConsistent() {
        UserService svc = mock(UserService.class);
        when(svc.list(any())).thenReturn(page(1, 200, 1, List.of(sampleView())));
        UserExportTask task = new UserExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals(1, rows.size());
        assertEquals(task.headers().size(), rows.get(0).size(), "行宽必须与表头一致");
        assertEquals("1001", rows.get(0).get(0));
        assertEquals("admin", rows.get(0).get(1));
        assertEquals("超级管理员", rows.get(0).get(6), "多角色以「、」拼接");
        assertEquals("总经理、技术总监", rows.get(0).get(8), "多岗位以「、」拼接");
        assertEquals("22644212509", rows.get(0).get(3), "手机号透传");
    }

    @Test
    void joinsEmptyCollectionsAsBlank() {
        UserService svc = mock(UserService.class);
        when(svc.list(any())).thenReturn(page(1, 200, 1, List.of(UserView.from(
                1002L, "noob", "新人", null, null,
                List.of(), List.of(), List.of(),
                List.of(20L), List.of("ACME 集团"), List.of(), List.of(),
                "ACTIVE", null))));
        UserExportTask task = new UserExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals("", rows.get(0).get(6), "无角色应为空串");
        assertEquals("", rows.get(0).get(8), "无岗位应为空串");
        assertEquals("", rows.get(0).get(9), "null 创建时间应为空串");
    }

    @Test
    void passesFilterParamsThrough() {
        UserService svc = mock(UserService.class);
        when(svc.list(any())).thenReturn(page(1, 200, 0, List.of()));
        UserExportTask task = new UserExportTask(svc);

        task.rows(Map.of("username", "admin", "status", "DISABLED", "orgId", "20", "roleId", "10"));

        ArgumentCaptor<UserQuery> captor = ArgumentCaptor.forClass(UserQuery.class);
        verify(svc).list(captor.capture());
        UserQuery query = captor.getValue();
        assertEquals("admin", query.username());
        assertEquals("DISABLED", query.status());
        assertEquals(20L, query.orgId());
        assertEquals(10L, query.roleId());
        assertEquals(200, query.size());
    }

    @Test
    void ignoresMalformedIdFilters() {
        UserService svc = mock(UserService.class);
        when(svc.list(any())).thenReturn(page(1, 200, 0, List.of()));
        UserExportTask task = new UserExportTask(svc);

        task.rows(Map.of("orgId", "not-a-number", "roleId", ""));

        ArgumentCaptor<UserQuery> captor = ArgumentCaptor.forClass(UserQuery.class);
        verify(svc).list(captor.capture());
        assertEquals(null, captor.getValue().orgId(), "非法 orgId 应忽略（视作不过滤）");
        assertEquals(null, captor.getValue().roleId(), "空 roleId 应忽略");
    }

    @Test
    void pagesThroughUntilTotalCollected() {
        UserService svc = mock(UserService.class);
        when(svc.list(any())).thenReturn(page(1, 200, 250, sampleViews(200)))
                .thenReturn(page(2, 200, 250, sampleViews(50)));
        UserExportTask task = new UserExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals(250, rows.size(), "两页应拼齐 total=250 行");
        verify(svc, times(2)).list(any());
    }

    @Test
    void breaksOnUnexpectedEmptyPage() {
        UserService svc = mock(UserService.class);
        // 首页 200 行但 total 虚报 500（并发删除致空洞）：第二页空 → 立即中断，不死循环
        when(svc.list(argThat(q -> q != null && q.page() == 1))).thenReturn(page(1, 200, 500, sampleViews(200)));
        when(svc.list(argThat(q -> q != null && q.page() == 2))).thenReturn(page(2, 200, 500, List.of()));
        UserExportTask task = new UserExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals(200, rows.size());
        verify(svc, times(2)).list(any());
    }

    @Test
    void truncatesAtMaxRows() {
        UserService svc = mock(UserService.class);
        // total 超上限：首页定上限，翻页填满后截断（mock 恒定返回同页数据）
        when(svc.list(any())).thenReturn(page(1, 200, 10_001, sampleViews(200)));
        UserExportTask task = new UserExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals(10_000, rows.size(), "超出 10000 行应截断");
    }

    private static List<UserView> sampleViews(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> UserView.from((long) (2000 + i), "u" + i, "用户" + i, null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        "ACTIVE", null))
                .toList();
    }
}
