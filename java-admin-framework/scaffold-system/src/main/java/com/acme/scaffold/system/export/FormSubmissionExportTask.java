package com.acme.scaffold.system.export;

import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.system.form.FormSubmissionService;
import com.acme.scaffold.system.form.dto.FormSubmissionAdminQuery;
import com.acme.scaffold.system.form.dto.FormSubmissionManageView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 表单提交记录导出（M47 导出中心内置模板）。
 *
 * <p><b>设计意图</b>：仅实现 {@link ExportTask} 契约即被导出中心自动发现并列举——
 * <b>无需新增接口、迁移或前端页面</b>，与 {@link LoginLogExportTask} 同范式。导出内容复用
 * {@link FormSubmissionService#exportList} 的跨表单检索能力（含表单名称回填），
 * 筛选参数（formCode / submittedBy / keyword / sortField / sortDir）由导出中心透传。</p>
 *
 * <p><b>脱敏</b>：导出文件离开系统边界，{@code data_json} 原样透传（与列表页口径一致，
 * 由前端按所属表单 Schema 还原展示），不在此处做额外处理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormSubmissionExportTask implements ExportTask {

    private final FormSubmissionService submissionService;

    @Override
    public String code() {
        return "form-submissions";
    }

    @Override
    public String displayName() {
        return "表单提交记录";
    }

    @Override
    public String fileName() {
        return "form-submissions";
    }

    @Override
    public List<String> headers() {
        return List.of("提交ID", "表单编码", "表单名称", "提交人", "提交内容", "提交时间");
    }

    @Override
    public List<List<String>> rows(Map<String, String> params) {
        FormSubmissionAdminQuery q = FormSubmissionAdminQuery.of(
                params.get("formCode"),
                params.get("submittedBy"),
                params.get("keyword"),
                params.get("sortField"),
                params.get("sortDir"),
                1, Integer.MAX_VALUE);
        List<FormSubmissionManageView> list = submissionService.exportList(q);
        return list.stream().map(v -> List.of(
                str(v.id()),
                str(v.formCode()),
                str(v.formName()),
                str(v.submittedBy()),
                str(v.dataJson()),
                str(v.createdAt())
        )).toList();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
