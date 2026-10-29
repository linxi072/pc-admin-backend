package com.acme.scaffold.system.export;

import com.acme.scaffold.system.form.FormSubmissionService;
import com.acme.scaffold.system.form.dto.FormSubmissionAdminQuery;
import com.acme.scaffold.system.form.dto.FormSubmissionManageView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormSubmissionExportTask} 单元测试：验证「导出中心模板注册」契约与参数透传。
 *
 * <p>重点锁定：表头与行宽一致（否则 CSV/XLSX 会错列）、筛选参数透传至
 * {@link FormSubmissionService#exportList}。表单名称已由 service 在导出取数阶段回填，
 * 本类只做透传，故此处直接使用含 formName 的视图值。</p>
 */
class FormSubmissionExportTaskTest {

    private static FormSubmissionManageView sampleView() {
        return new FormSubmissionManageView(1001L, "contact-feedback", "联系反馈",
                "tester", "{\"name\":\"张三\"}", java.time.LocalDateTime.of(2026, 10, 16, 9, 30));
    }

    @Test
    void declaresExpectedMetadata() {
        FormSubmissionExportTask task = new FormSubmissionExportTask(mock(FormSubmissionService.class));
        assertEquals("form-submissions", task.code());
        assertEquals("表单提交记录", task.displayName());
        assertEquals("form-submissions", task.fileName());
        // 单表结构（headers 非空）→ 导出中心自动支持 CSV + XLSX
        assertFalse(task.headers().isEmpty());
        assertEquals(6, task.headers().size());
    }

    @Test
    void headerAndRowWidthsAreConsistent() {
        FormSubmissionService svc = mock(FormSubmissionService.class);
        when(svc.exportList(any())).thenReturn(List.of(sampleView()));
        FormSubmissionExportTask task = new FormSubmissionExportTask(svc);

        List<List<String>> rows = task.rows(Map.of());
        assertEquals(1, rows.size());
        assertEquals(task.headers().size(), rows.get(0).size(), "行宽必须与表头一致");
        assertEquals("联系反馈", rows.get(0).get(2), "表单名称列应已回填");
        // 导出为系统边界文件，dataJson 原样透传（与列表页口径一致），不做字段抽取
        assertEquals("{\"name\":\"张三\"}", rows.get(0).get(4), "提交内容列应为 dataJson 原文");
    }

    @Test
    void passesFilterParamsToService() {
        FormSubmissionService svc = mock(FormSubmissionService.class);
        when(svc.exportList(any())).thenReturn(List.of());
        FormSubmissionExportTask task = new FormSubmissionExportTask(svc);

        task.rows(Map.of(
                "formCode", "contact-feedback",
                "submittedBy", "tester",
                "keyword", "张三",
                "sortField", "created_at",
                "sortDir", "DESC"));

        ArgumentCaptor<FormSubmissionAdminQuery> captor = ArgumentCaptor.forClass(FormSubmissionAdminQuery.class);
        verify(svc).exportList(captor.capture());
        FormSubmissionAdminQuery q = captor.getValue();
        assertEquals("contact-feedback", q.formCode());
        assertEquals("tester", q.submittedBy());
        assertEquals("张三", q.keyword());
        assertEquals("created_at", q.sortField());
        assertEquals("DESC", q.sortDir());
    }
}
