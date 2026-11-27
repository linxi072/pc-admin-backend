package com.acme.scaffold.system.form.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.form.domain.CreateFormDefinitionRequest;
import com.acme.scaffold.system.form.domain.UpdateFormDefinitionRequest;
import com.acme.scaffold.system.form.domain.model.FormDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;

/** 表单编码唯一键并发兜底回归：数据库冲突须转成清晰的业务 409，而非通用异常。 */
class FormDefinitionCodeConflictTest {

    private static final String SCHEMA =
            "{\"version\":1,\"title\":\"请假\",\"fields\":[{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"INPUT\"}]}";

    @Test
    void concurrentCreateShouldReturnConflict() {
        FormDefinitionService service = new FormDefinitionService(mock(DSLContext.class));

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.count(any(DSLContext.class), eq(SqlTables.FORM_DEFINITION),
                    any(Condition.class))).thenReturn(0L);
            writers.when(() -> SqlWriters.insert(any(DSLContext.class), eq(SqlTables.FORM_DEFINITION),
                    any(FormDefinition.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(
                    new CreateFormDefinitionRequest("LEAVE", "请假", SCHEMA, "NONE", true, null)));

            assertEquals("表单编码已存在", ex.getMessage());
        }
    }

    @Test
    void concurrentRenameShouldReturnConflict() {
        FormDefinitionService service = new FormDefinitionService(mock(DSLContext.class));

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.FORM_DEFINITION),
                    eq(FormDefinition.class), eq(1L))).thenReturn(existing());
            writers.when(() -> SqlWriters.updateById(any(DSLContext.class), eq(SqlTables.FORM_DEFINITION),
                    eq(1L), any(FormDefinition.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(1L,
                    new UpdateFormDefinitionRequest("LEAVE", "请假", SCHEMA, "NONE", true, null)));

            assertEquals("表单编码已存在", ex.getMessage());
        }
    }

    private static FormDefinition existing() {
        FormDefinition d = new FormDefinition();
        d.setId(1L);
        d.setFormCode("LEAVE");
        d.setFormName("请假");
        d.setSchemaJson(SCHEMA);
        d.setTargetType("NONE");
        d.setEnabled(1);
        return d;
    }
}
