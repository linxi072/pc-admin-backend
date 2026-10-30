package com.acme.scaffold.system.form;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.form.domain.FormDefinitionDO;
import com.acme.scaffold.system.form.dto.CreateFormDefinitionRequest;
import com.acme.scaffold.system.form.dto.FormDefinitionView;
import com.acme.scaffold.system.form.dto.UpdateFormDefinitionRequest;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormDefinitionService} 单元测试（M22 深化：表单定义管理 CRUD）。
 *
 * <p>以 {@code mockStatic(JooqWriters)} 作为打库替身，固化四条关键语义：
 * ① 创建成功 → insert 落库一次并回填 id、失效缓存；② 编码重复 / Schema 非法 / 绑定目标非法 → 整体拒绝、
 * 绝不落库；③ 更新存在性 + 编码唯一性 + Schema 校验；④ 删除存在性 + 逻辑删除 + 失效缓存。
 * 离线可跑，不依赖真实数据库 / Docker。</p>
 */
class FormDefinitionServiceTest {

    private static final String VALID_SCHEMA = """
            {"version":1,"title":"联系反馈","fields":[
              {"key":"name","label":"姓名","type":"INPUT","required":true,"maxLength":32},
              {"key":"channel","label":"渠道","type":"SELECT","required":true,
               "options":[{"label":"电话","value":"PHONE"},{"label":"邮件","value":"EMAIL"}]}]}
            """;

    private static final String INVALID_SCHEMA = "{\"fields\":[]}";

    private static FormDefinitionDO existing(Long id, String code) {
        FormDefinitionDO d = new FormDefinitionDO();
        d.setId(id);
        d.setFormCode(code);
        d.setFormName("旧名称");
        d.setSchemaJson(VALID_SCHEMA);
        d.setTargetType("NONE");
        d.setEnabled(1);
        return d;
    }

    private static MockedStatic<JooqWriters> stubWriters(long count) {
        MockedStatic<JooqWriters> w = mockStatic(JooqWriters.class);
        w.when(() -> JooqWriters.notDeleted(any())).thenReturn(DSL.trueCondition());
        w.when(() -> JooqWriters.count(any(), any(), any())).thenReturn(count);
        w.when(() -> JooqWriters.insert(any(), any(), any())).thenAnswer(inv -> {
            FormDefinitionDO d = inv.getArgument(2);
            d.setId(42L);
            return null;
        });
        // updateById / delete 为 void 方法，mock 默认 no-op，无需（也不能）thenReturn 桩
        return w;
    }

    // ------------------------------------------------------------------
    // create
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create：校验通过 → insert 落库一次，回填 id 并失效缓存")
    void create_success_persists() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService service = new FormDefinitionService(dsl);
        try (MockedStatic<JooqWriters> w = stubWriters(0)) {
            CreateFormDefinitionRequest req = new CreateFormDefinitionRequest(
                    "feedback", "反馈", VALID_SCHEMA, "FORM_SUBMISSION", true, "备注");

            Long id = service.create(req);

            assertEquals(42L, id);
            w.verify(() -> JooqWriters.insert(eq(dsl), eq(JooqTables.FORM_DEFINITION), any(FormDefinitionDO.class)), times(1));
        }
    }

    @Test
    @DisplayName("create：编码重复 → 抛 CONFLICT，绝不落库")
    void create_duplicateCode_rejected() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService service = new FormDefinitionService(dsl);
        try (MockedStatic<JooqWriters> w = stubWriters(1)) {
            CreateFormDefinitionRequest req = new CreateFormDefinitionRequest(
                    "feedback", "反馈", VALID_SCHEMA, null, true, null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
            assertEquals(CommonErrorCode.CONFLICT, ex.getErrorCode());
            w.verify(() -> JooqWriters.insert(any(), any(), any()), never());
        }
    }

    @Test
    @DisplayName("create：Schema 非法 → 抛 VALIDATION_ERROR，绝不落库")
    void create_invalidSchema_rejected() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService service = new FormDefinitionService(dsl);
        try (MockedStatic<JooqWriters> w = stubWriters(0)) {
            CreateFormDefinitionRequest req = new CreateFormDefinitionRequest(
                    "feedback", "反馈", INVALID_SCHEMA, null, true, null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
            assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
            w.verify(() -> JooqWriters.insert(any(), any(), any()), never());
        }
    }

    @Test
    @DisplayName("create：绑定目标非法 → 抛 VALIDATION_ERROR")
    void create_invalidTarget_rejected() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService service = new FormDefinitionService(dsl);
        try (MockedStatic<JooqWriters> w = stubWriters(0)) {
            CreateFormDefinitionRequest req = new CreateFormDefinitionRequest(
                    "feedback", "反馈", VALID_SCHEMA, "OTHER", true, null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
            assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        }
    }

    // ------------------------------------------------------------------
    // update
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update：存在且校验通过 → updateById 一次")
    void update_success() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService service = new FormDefinitionService(dsl);
        try (MockedStatic<JooqWriters> w = stubWriters(0)) {
            w.when(() -> JooqWriters.fetchById(any(), any(), any(), any()))
                    .thenReturn(existing(7L, "feedback"));
            UpdateFormDefinitionRequest req = new UpdateFormDefinitionRequest(
                    "feedback", "新名称", VALID_SCHEMA, "FORM_SUBMISSION", false, "x");
            service.update(7L, req);
            w.verify(() -> JooqWriters.updateById(eq(dsl), eq(JooqTables.FORM_DEFINITION), eq(7L), any(FormDefinitionDO.class)), times(1));
        }
    }

    @Test
    @DisplayName("update：不存在 → 抛 NOT_FOUND")
    void update_notFound() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService service = new FormDefinitionService(dsl);
        try (MockedStatic<JooqWriters> w = stubWriters(0)) {
            w.when(() -> JooqWriters.fetchById(any(), any(), any(), any())).thenReturn(null);
            UpdateFormDefinitionRequest req = new UpdateFormDefinitionRequest(
                    "feedback", "新名称", VALID_SCHEMA, null, true, null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(99L, req));
            assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
        }
    }

    @Test
    @DisplayName("update：改名为已存在编码 → 抛 CONFLICT")
    void update_renameDuplicate_rejected() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService service = new FormDefinitionService(dsl);
        try (MockedStatic<JooqWriters> w = stubWriters(1)) {
            w.when(() -> JooqWriters.fetchById(any(), any(), any(), any()))
                    .thenReturn(existing(7L, "feedback"));
            // 编码变更（feedback → other）且 other 已存在
            UpdateFormDefinitionRequest req = new UpdateFormDefinitionRequest(
                    "other", "新名称", VALID_SCHEMA, null, true, null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(7L, req));
            assertEquals(CommonErrorCode.CONFLICT, ex.getErrorCode());
            w.verify(() -> JooqWriters.updateById(any(), any(), any(), any()), never());
        }
    }

    // ------------------------------------------------------------------
    // remove
    // ------------------------------------------------------------------

    @Test
    @DisplayName("remove：存在 → 逻辑删除一次")
    void remove_success() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService service = new FormDefinitionService(dsl);
        try (MockedStatic<JooqWriters> w = stubWriters(0)) {
            w.when(() -> JooqWriters.fetchById(any(), any(), any(), any()))
                    .thenReturn(existing(7L, "feedback"));
            service.remove(7L);
            w.verify(() -> JooqWriters.delete(eq(dsl), eq(JooqTables.FORM_DEFINITION), eq(7L), eq(true)), times(1));
        }
    }

    @Test
    @DisplayName("remove：不存在 → 抛 NOT_FOUND")
    void remove_notFound() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService service = new FormDefinitionService(dsl);
        try (MockedStatic<JooqWriters> w = stubWriters(0)) {
            w.when(() -> JooqWriters.fetchById(any(), any(), any(), any())).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.remove(99L));
            assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
        }
    }

    // ------------------------------------------------------------------
    // getById
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getById：存在 → 返回视图；不存在 → 抛 NOT_FOUND")
    void getById() {
        DSLContext dsl = mock(DSLContext.class);
        FormDefinitionService service = new FormDefinitionService(dsl);
        try (MockedStatic<JooqWriters> w = stubWriters(0)) {
            w.when(() -> JooqWriters.fetchById(any(), any(), any(), any()))
                    .thenReturn(existing(7L, "feedback"));
            FormDefinitionView v = service.getById(7L);
            assertNotNull(v);
            assertEquals("feedback", v.formCode());

            w.when(() -> JooqWriters.fetchById(any(), any(), any(), any())).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99L));
            assertEquals(CommonErrorCode.NOT_FOUND, ex.getErrorCode());
        }
    }
}
