package com.acme.scaffold.system.dict.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.dict.domain.CreateDictTypeRequest;
import com.acme.scaffold.system.dict.domain.UpdateDictTypeRequest;
import com.acme.scaffold.system.dict.domain.model.SysDictType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;

/** 字典编码唯一键并发兜底回归：数据库冲突须转成与预校验一致的业务 409，正常路径不受影响。 */
class DictTypeServiceCodeConflictTest {

    private static DictTypeService service() {
        return new DictTypeService(mock(DSLContext.class));
    }

    private static CreateDictTypeRequest createRequest() {
        return new CreateDictTypeRequest("sys_notice_type", "通知类型", "ACTIVE", 1, "测试");
    }

    @Test
    void concurrentCreateShouldReturnConflict() {
        DictTypeService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);
            writers.when(() -> SqlWriters.insert(any(DSLContext.class), eq(SqlTables.SYS_DICT_TYPE),
                    any(SysDictType.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(createRequest()));

            assertEquals("字典编码已存在", ex.getMessage());
        }
    }

    @Test
    void concurrentCodeChangeOnUpdateShouldReturnConflict() {
        DictTypeService service = service();

        SysDictType existing = new SysDictType();
        existing.setId(9L);
        existing.setDictCode("OLD_CODE");
        existing.setDictName("旧名称");
        existing.setStatus("ACTIVE");
        existing.setSortNo(1);

        UpdateDictTypeRequest req = new UpdateDictTypeRequest("NEW_CODE", "新名称", null, null, null);

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.SYS_DICT_TYPE),
                    eq(SysDictType.class), eq(9L))).thenReturn(existing);
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);
            writers.when(() -> SqlWriters.updateById(any(DSLContext.class), eq(SqlTables.SYS_DICT_TYPE),
                    eq(9L), any(SysDictType.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(9L, req));

            assertEquals("字典编码已存在", ex.getMessage());
        }
    }

    @Test
    void normalCreateShouldNotBeSwallowedByConflictGuard() {
        DictTypeService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);

            assertDoesNotThrow(() -> service.create(createRequest()));
        }
    }
}
