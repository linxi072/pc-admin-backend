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
import com.acme.scaffold.system.dict.domain.CreateDictDataRequest;
import com.acme.scaffold.system.dict.domain.UpdateDictDataRequest;
import com.acme.scaffold.system.dict.domain.model.SysDictData;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;

/** 字典值唯一键并发兜底回归：数据库冲突须转成与预校验一致的业务 409，正常路径不受影响。 */
class DictDataServiceValueConflictTest {

    private static DictDataService service() {
        return new DictDataService(mock(DSLContext.class));
    }

    private static CreateDictDataRequest createRequest() {
        return new CreateDictDataRequest("sys_user_sex", "男性", "M", 1, "ACTIVE", null);
    }

    private static SysDictData existing() {
        SysDictData d = new SysDictData();
        d.setId(31L);
        d.setDictTypeCode("sys_user_sex");
        d.setDictLabel("旧标签");
        d.setDictValue("OLD");
        d.setDictSort(1);
        d.setStatus("ACTIVE");
        return d;
    }

    @Test
    void concurrentCreateShouldReturnConflict() {
        DictDataService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);
            writers.when(() -> SqlWriters.insert(any(DSLContext.class), eq(SqlTables.SYS_DICT_DATA),
                    any(SysDictData.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(createRequest()));

            assertEquals("该字典类型下字典值已存在", ex.getMessage());
        }
    }

    @Test
    void concurrentValueChangeOnUpdateShouldReturnConflict() {
        DictDataService service = service();

        UpdateDictDataRequest req = new UpdateDictDataRequest("sys_user_sex", "新标签", "NEW", null, null, null);

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.SYS_DICT_DATA),
                    eq(SysDictData.class), eq(31L))).thenReturn(existing());
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);
            writers.when(() -> SqlWriters.updateById(any(DSLContext.class), eq(SqlTables.SYS_DICT_DATA),
                    eq(31L), any(SysDictData.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(31L, req));

            assertEquals("该字典类型下字典值已存在", ex.getMessage());
        }
    }

    @Test
    void normalCreateShouldNotBeSwallowedByConflictGuard() {
        DictDataService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);

            assertDoesNotThrow(() -> service.create(createRequest()));
        }
    }
}
