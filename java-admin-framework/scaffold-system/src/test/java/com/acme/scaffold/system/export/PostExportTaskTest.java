package com.acme.scaffold.system.export;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.system.dto.PostQuery;
import com.acme.scaffold.system.dto.PostView;
import com.acme.scaffold.system.service.PostService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PostExportTask} 单元测试：模板契约、翻页取数与筛选透传。
 *
 * <p>翻页 / 截断策略与 {@link UserExportTask} 同构，此处重点锁定 keyword/status 透传
 * （与岗位管理列表页同口径）与空页防御。
 */
class PostExportTaskTest {

    private static PostView sampleView() {
        return new PostView(30L, "GM", "总经理", 1, "ACTIVE", "公司负责人",
                LocalDateTime.of(2026, 10, 20, 8, 30));
    }

    private static PageResult<PostView> page(int pageNo, long total, List<PostView> records) {
        return new PageResult<>(pageNo, 200, total, records);
    }

    @Test
    void declaresExpectedMetadata() {
        PostExportTask task = new PostExportTask(mock(PostService.class));
        assertEquals("posts", task.code());
        assertEquals("岗位列表", task.displayName());
        assertEquals("post", task.fileName());
        assertFalse(task.headers().isEmpty());
    }

    @Test
    void headerAndRowWidthsAreConsistent() {
        PostService svc = mock(PostService.class);
        when(svc.list(any())).thenReturn(page(1, 1, List.of(sampleView())));
        PostExportTask task = new PostExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals(1, rows.size());
        assertEquals(task.headers().size(), rows.get(0).size(), "行宽必须与表头一致");
        assertEquals("30", rows.get(0).get(0));
        assertEquals("GM", rows.get(0).get(1));
        assertEquals("总经理", rows.get(0).get(2));
        assertEquals("1", rows.get(0).get(3));
        assertEquals("ACTIVE", rows.get(0).get(4));
        assertEquals("公司负责人", rows.get(0).get(5));
    }

    @Test
    void nullRemarkRendersAsBlank() {
        PostService svc = mock(PostService.class);
        when(svc.list(any())).thenReturn(page(1, 1, List.of(
                new PostView(31L, "DEV", "开发工程师", 5, "ACTIVE", null, null))));
        PostExportTask task = new PostExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals("", rows.get(0).get(5), "null 备注应归一为空串");
    }

    @Test
    void passesFilterParamsThrough() {
        PostService svc = mock(PostService.class);
        when(svc.list(any())).thenReturn(page(1, 0, List.of()));
        PostExportTask task = new PostExportTask(svc);

        task.rows(Map.of("keyword", "经理", "status", "DISABLED"));

        ArgumentCaptor<PostQuery> captor = ArgumentCaptor.forClass(PostQuery.class);
        verify(svc).list(captor.capture());
        assertEquals("经理", captor.getValue().keyword());
        assertEquals("DISABLED", captor.getValue().status());
        assertEquals(200, captor.getValue().size());
    }

    @Test
    void pagesThroughUntilTotalCollected() {
        PostService svc = mock(PostService.class);
        when(svc.list(argThat(q -> q != null && q.page() == 1))).thenReturn(page(1, 250, java.util.stream.IntStream.range(0, 200)
                .mapToObj(i -> new PostView((long) i, "P" + i, "岗位" + i, 0, "ACTIVE", null, null)).toList()));
        when(svc.list(argThat(q -> q != null && q.page() == 2))).thenReturn(page(2, 250, java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> new PostView((long) i, "P" + i, "岗位" + i, 0, "ACTIVE", null, null)).toList()));
        PostExportTask task = new PostExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals(250, rows.size(), "两页应拼齐 total=250 行");
        verify(svc, times(2)).list(any());
    }

    @Test
    void breaksOnUnexpectedEmptyPage() {
        PostService svc = mock(PostService.class);
        when(svc.list(argThat(q -> q != null && q.page() == 1))).thenReturn(page(1, 500, java.util.stream.IntStream.range(0, 200)
                .mapToObj(i -> new PostView((long) i, "P" + i, "岗位" + i, 0, "ACTIVE", null, null)).toList()));
        when(svc.list(argThat(q -> q != null && q.page() == 2))).thenReturn(page(2, 500, List.of()));
        PostExportTask task = new PostExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals(200, rows.size(), "空页应中断循环而非死循环");
        verify(svc, times(2)).list(any());
    }
}
