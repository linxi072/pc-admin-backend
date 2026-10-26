package com.acme.scaffold.system.form;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.form.domain.FormSubmissionDO;
import com.acme.scaffold.system.form.dto.FormDefinitionView;
import com.acme.scaffold.system.form.dto.FormSubmissionView;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * {@link FormSubmissionService} 单元测试：以 {@code mockStatic(JooqWriters)} 作为打库替身，
 * 固化 M22 正式立项的两条关键语义——提交校验通过才落库、校验失败整体拒绝不落库、提交记录分页映射。
 * 离线可跑，不依赖真实数据库 / Docker。
 */
class FormSubmissionServiceTest {

    private static final String FORM_CODE = "contact-feedback";
    private static final String VALID_SCHEMA = """
            {
              "version": 1,
              "title": "联系反馈",
              "fields": [
                {"key": "name", "label": "姓名", "type": "INPUT", "required": true, "maxLength": 32},
                {"key": "channel", "label": "反馈渠道", "type": "SELECT", "required": true,
                 "options": [{"label": "电话", "value": "PHONE"}, {"label": "邮件", "value": "EMAIL"}]},
                {"key": "urgent", "label": "是否紧急", "type": "SWITCH"},
                {"key": "content", "label": "反馈内容", "type": "TEXTAREA", "required": true,
                 "minLength": 10, "maxLength": 500}
              ]
            }
            """;

    private static final Map<String, Object> VALID_VALUES = Map.of(
            "name", "张三",
            "channel", "PHONE",
            "urgent", false,
            "content", "这是一条足够长的反馈内容用于覆盖最小长度校验");

    private static FormDefinitionView enabledDef() {
        return new FormDefinitionView(1L, FORM_CODE, "联系反馈", VALID_SCHEMA,
                "FORM_SUBMISSION", true, "remark", null, null);
    }

    @Test
    @DisplayName("submit：校验通过 → JooqWriters.insert 落库一次，回填 id 与提交人")
    void submit_success_persists() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService definitionService = mock(FormDefinitionService.class);
        when(definitionService.getEnabledByCode(FORM_CODE)).thenReturn(enabledDef());

        FormSubmissionService service = new FormSubmissionService(dsl, definitionService);
        try (MockedStatic<JooqWriters> w = mockStatic(JooqWriters.class)) {
            ArgumentCaptor<FormSubmissionDO> captor = ArgumentCaptor.forClass(FormSubmissionDO.class);
            w.when(() -> JooqWriters.insert(any(), any(), any())).thenAnswer(inv -> null);

            FormSubmissionView view = service.submit(FORM_CODE, VALID_VALUES, "tester");

            w.verify(() -> JooqWriters.insert(eq(dsl), eq(JooqTables.FORM_SUBMISSION), captor.capture()));
            FormSubmissionDO saved = captor.getValue();
            assertEquals(1L, saved.getFormDefinitionId());
            assertEquals(FORM_CODE, saved.getFormCode());
            assertEquals("tester", saved.getSubmittedBy());
            assertNotNull(saved.getDataJson());
            assertTrue(saved.getDataJson().contains("张三"));
            assertTrue(saved.getDataJson().contains("PHONE"));
            // 视图回显：dataJson 与落库一致
            assertEquals(saved.getDataJson(), view.dataJson());
        }
    }

    @Test
    @DisplayName("submit：校验失败（未知字段 + 必填缺失）→ 抛 BusinessException 且绝不落库")
    void submit_validationFailed_noPersist() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService definitionService = mock(FormDefinitionService.class);
        when(definitionService.getEnabledByCode(FORM_CODE)).thenReturn(enabledDef());

        Map<String, Object> bad = new java.util.HashMap<>(VALID_VALUES);
        bad.remove("name");      // 必填缺失
        bad.put("hack", "x");    // 未知字段

        FormSubmissionService service = new FormSubmissionService(dsl, definitionService);
        try (MockedStatic<JooqWriters> w = mockStatic(JooqWriters.class)) {
            java.util.concurrent.atomic.AtomicBoolean insertCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
            w.when(() -> JooqWriters.insert(any(), any(), any())).thenAnswer(inv -> {
                insertCalled.set(true);
                return null;
            });

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.submit(FORM_CODE, bad, "tester"));
            assertTrue(ex.getMessage().contains("表单校验未通过"));
            // 校验失败整体拒绝：绝不触达落库
            assertTrue(!insertCalled.get(), "校验失败时不应落库");
        }
    }

    @Test
    @DisplayName("listByForm：分页查询映射为视图，total/records 与打库返回一致")
    void listByForm_mapsPage() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService definitionService = mock(FormDefinitionService.class);
        FormSubmissionService service = new FormSubmissionService(dsl, definitionService);

        FormSubmissionDO row = new FormSubmissionDO();
        row.setId(99L);
        row.setFormCode(FORM_CODE);
        row.setSubmittedBy("tester");
        row.setDataJson("{\"name\":\"张三\"}");

        try (MockedStatic<JooqWriters> w = mockStatic(JooqWriters.class)) {
            w.when(() -> JooqWriters.notDeleted(any())).thenReturn(DSL.trueCondition());
            w.when(() -> JooqWriters.page(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageResult<>(1, 10, 1L, List.of(row)));

            PageResult<FormSubmissionView> result = service.listByForm(FORM_CODE, 1, 10);

            assertEquals(1L, result.total());
            assertEquals(1, result.records().size());
            FormSubmissionView v = result.records().get(0);
            // data_json 列存储的是整段提交值序列化的 JSON 字符串，而非单个字段值
            assertEquals("{\"name\":\"张三\"}", v.dataJson());
            assertEquals("tester", v.submittedBy());
            // 分页条件确实按 form_code 过滤
            w.verify(() -> JooqWriters.page(eq(dsl), eq(JooqTables.FORM_SUBMISSION),
                    eq(FormSubmissionDO.class), any(Condition.class), any(), any()));
        }
    }
}
