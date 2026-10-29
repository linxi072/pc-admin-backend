package com.acme.scaffold.system.form;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.form.domain.FormSubmissionDO;
import com.acme.scaffold.system.form.dto.FormDefinitionView;
import com.acme.scaffold.system.form.dto.FormSubmissionAdminQuery;
import com.acme.scaffold.system.form.dto.FormSubmissionFormCount;
import com.acme.scaffold.system.form.dto.FormSubmissionManageView;
import com.acme.scaffold.system.form.dto.FormSubmissionStat;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormSubmissionService} 管理面（M47 跨表单提交管理）单元测试：以
 * {@code mockStatic(JooqWriters)} 作为打库替身，固化 adminPage / getManageById /
 * removeSubmission / stats / exportList 的关键语义——表单名称回填、逻辑删除、按表单聚合、
 * 不存在记录抛 NOT_FOUND。离线可跑，不依赖真实数据库 / Docker。
 */
class FormSubmissionAdminServiceTest {

    private static final String CODE_A = "contact-feedback";
    private static final String NAME_A = "联系反馈";
    private static final String CODE_B = "survey";
    private static final String NAME_B = "问卷调查";

    private static FormSubmissionDO row(Long id, String code, String submittedBy) {
        FormSubmissionDO d = new FormSubmissionDO();
        d.setId(id);
        d.setFormCode(code);
        d.setSubmittedBy(submittedBy);
        d.setDataJson("{\"name\":\"张三\"}");
        return d;
    }

    private static FormDefinitionView def(String code, String name) {
        return new FormDefinitionView(1L, code, name, "{}", "FORM_SUBMISSION", true, "r", null, null);
    }

    private static FormSubmissionService service(DSLContext dsl, FormDefinitionService defSvc) {
        return new FormSubmissionService(dsl, defSvc);
    }

    // ------------------------------------------------------------------
    // adminPage
    // ------------------------------------------------------------------

    @Test
    @DisplayName("adminPage：分页映射为管理视图并回填表单名称")
    void adminPage_mapsFormNameAndPaging() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService defSvc = mock(FormDefinitionService.class);
        when(defSvc.list()).thenReturn(List.of(def(CODE_A, NAME_A)));

        FormSubmissionService svc = service(dsl, defSvc);
        try (MockedStatic<JooqWriters> w = mockStatic(JooqWriters.class)) {
            w.when(() -> JooqWriters.notDeleted(any())).thenReturn(DSL.trueCondition());
            w.when(() -> JooqWriters.page(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageResult<>(1, 10, 1L, List.of(row(10L, CODE_A, "tester"))));

            PageResult<FormSubmissionManageView> r = svc.adminPage(
                    FormSubmissionAdminQuery.of(null, null, null, null, null, 1, 10));

            assertEquals(1L, r.total());
            assertEquals(1, r.records().size());
            FormSubmissionManageView v = r.records().get(0);
            assertEquals(CODE_A, v.formCode());
            assertEquals(NAME_A, v.formName(), "表单名称应由编码映射回填");
            assertEquals("tester", v.submittedBy());
            w.verify(() -> JooqWriters.page(eq(dsl), eq(JooqTables.FORM_SUBMISSION),
                    eq(FormSubmissionDO.class), any(Condition.class), any(), any()));
        }
    }

    @Test
    @DisplayName("adminPage：排序字段白名单——非法字段回退 created_at，方向回退 DESC")
    void adminPage_sortWhitelistFallsBack() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService defSvc = mock(FormDefinitionService.class);
        when(defSvc.list()).thenReturn(List.of());

        FormSubmissionService svc = service(dsl, defSvc);
        try (MockedStatic<JooqWriters> w = mockStatic(JooqWriters.class)) {
            w.when(() -> JooqWriters.notDeleted(any())).thenReturn(DSL.trueCondition());
            w.when(() -> JooqWriters.page(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageResult<>(1, 10, 0L, List.of()));

            // 传入非法 sortField / sortDir，应不抛异常且仍能完成分页
            PageResult<FormSubmissionManageView> r = svc.adminPage(
                    FormSubmissionAdminQuery.of(null, null, null, "hack", "SIDEWAYS", 1, 10));
            assertEquals(0L, r.total());
        }
    }

    // ------------------------------------------------------------------
    // getManageById
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getManageById：不存在记录抛 NOT_FOUND")
    void getManageById_notFound_throws() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService defSvc = mock(FormDefinitionService.class);
        FormSubmissionService svc = service(dsl, defSvc);

        try (MockedStatic<JooqWriters> w = mockStatic(JooqWriters.class)) {
            w.when(() -> JooqWriters.fetchById(any(), any(), any(), eq(99L))).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.getManageById(99L));
            assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
        }
    }

    @Test
    @DisplayName("getManageById：存在记录回填表单名称")
    void getManageById_found_returnsViewWithName() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService defSvc = mock(FormDefinitionService.class);
        when(defSvc.list()).thenReturn(List.of(def(CODE_A, NAME_A)));
        FormSubmissionService svc = service(dsl, defSvc);

        try (MockedStatic<JooqWriters> w = mockStatic(JooqWriters.class)) {
            w.when(() -> JooqWriters.fetchById(any(), any(), any(), eq(10L)))
                    .thenReturn(row(10L, CODE_A, "tester"));
            FormSubmissionManageView v = svc.getManageById(10L);
            assertEquals(10L, v.id());
            assertEquals(NAME_A, v.formName());
        }
    }

    // ------------------------------------------------------------------
    // removeSubmission
    // ------------------------------------------------------------------

    @Test
    @DisplayName("removeSubmission：不存在记录抛 NOT_FOUND 且不触达删除")
    void removeSubmission_notFound_throws() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService defSvc = mock(FormDefinitionService.class);
        FormSubmissionService svc = service(dsl, defSvc);

        try (MockedStatic<JooqWriters> w = mockStatic(JooqWriters.class)) {
            w.when(() -> JooqWriters.fetchById(any(), any(), any(), eq(99L))).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.removeSubmission(99L));
            assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
            w.verify(() -> JooqWriters.delete(any(), any(), any(), anyBoolean()), never());
        }
    }

    @Test
    @DisplayName("removeSubmission：存在记录走逻辑删除（logical=true）")
    void removeSubmission_found_logicalDeletes() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService defSvc = mock(FormDefinitionService.class);
        FormSubmissionService svc = service(dsl, defSvc);

        try (MockedStatic<JooqWriters> w = mockStatic(JooqWriters.class)) {
            w.when(() -> JooqWriters.fetchById(any(), any(), any(), eq(7L)))
                    .thenReturn(row(7L, CODE_A, "tester"));
            w.when(() -> JooqWriters.delete(any(), any(), any(), anyBoolean())).thenAnswer(inv -> null);

            svc.removeSubmission(7L);

            w.verify(() -> JooqWriters.delete(eq(dsl), eq(JooqTables.FORM_SUBMISSION), eq(7L), eq(true)));
        }
    }

    // ------------------------------------------------------------------
    // stats
    // ------------------------------------------------------------------

    @Test
    @DisplayName("stats：按表单编码聚合计数，总数与明细一致且按数量降序")
    void stats_aggregatesByForm() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService defSvc = mock(FormDefinitionService.class);
        when(defSvc.list()).thenReturn(List.of(def(CODE_A, NAME_A), def(CODE_B, NAME_B)));
        FormSubmissionService svc = service(dsl, defSvc);

        try (MockedStatic<JooqWriters> w = mockStatic(JooqWriters.class)) {
            w.when(() -> JooqWriters.notDeleted(any())).thenReturn(DSL.trueCondition());
            w.when(() -> JooqWriters.count(any(), any(), any())).thenReturn(3L);
            w.when(() -> JooqWriters.fetchList(any(), any(), any(), any()))
                    .thenReturn(List.of(
                            row(1L, CODE_A, "a"),
                            row(2L, CODE_A, "b"),
                            row(3L, CODE_B, "c")));

            FormSubmissionStat stat = svc.stats();

            assertEquals(3L, stat.totalCount());
            assertEquals(2, stat.byForm().size());
            FormSubmissionFormCount first = stat.byForm().get(0);
            assertEquals(CODE_A, first.formCode());
            assertEquals(NAME_A, first.formName());
            assertEquals(2L, first.count());
            assertEquals(CODE_B, stat.byForm().get(1).formCode());
            assertEquals(1L, stat.byForm().get(1).count());
        }
    }

    // ------------------------------------------------------------------
    // exportList
    // ------------------------------------------------------------------

    @Test
    @DisplayName("exportList：全量映射为管理视图并回填表单名称")
    void exportList_mapsAllRows() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService defSvc = mock(FormDefinitionService.class);
        when(defSvc.list()).thenReturn(List.of(def(CODE_A, NAME_A)));
        FormSubmissionService svc = service(dsl, defSvc);

        try (MockedStatic<JooqWriters> w = mockStatic(JooqWriters.class)) {
            w.when(() -> JooqWriters.notDeleted(any())).thenReturn(DSL.trueCondition());
            // exportList 走 5 参 fetchList（dsl, table, Class, cond, order）；stats 走 4 参（无排序）——
            // 此处须与 exportList 的实际调用签名对齐，否则桩不匹配返回空列表。
            w.when(() -> JooqWriters.fetchList(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(row(1L, CODE_A, "a"), row(2L, CODE_A, "b")));

            List<FormSubmissionManageView> rows = svc.exportList(
                    FormSubmissionAdminQuery.of(null, null, null, null, null, 1, Integer.MAX_VALUE));

            assertEquals(2, rows.size());
            assertTrue(rows.stream().allMatch(r -> NAME_A.equals(r.formName())));
        }
    }
}
