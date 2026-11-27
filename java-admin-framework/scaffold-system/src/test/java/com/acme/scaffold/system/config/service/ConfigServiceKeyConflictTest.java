package com.acme.scaffold.system.config.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.config.domain.CreateConfigRequest;
import com.acme.scaffold.system.config.domain.UpdateConfigRequest;
import com.acme.scaffold.system.config.domain.model.SysConfig;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;

/** 配置键唯一键并发兜底回归：数据库冲突须转成与预校验一致的业务 409，正常路径不受影响。 */
class ConfigServiceKeyConflictTest {

    private static CreateConfigRequest createRequest() {
        return new CreateConfigRequest("app.file.max-bytes", "上传大小上限", "10485760",
                null, "INT", null, 1, "ACTIVE");
    }

    @Test
    void concurrentCreateShouldReturnConflict() {
        ConfigService service = new ConfigService(mock(DSLContext.class));

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);
            writers.when(() -> SqlWriters.insert(any(DSLContext.class), eq(SqlTables.SYS_CONFIG),
                    any(SysConfig.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(createRequest()));

            assertEquals("配置键已存在", ex.getMessage());
        }
    }

    @Test
    void concurrentRenameShouldReturnConflict() {
        ConfigService service = new ConfigService(mock(DSLContext.class));

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.SYS_CONFIG),
                    eq(SysConfig.class), eq(1L))).thenReturn(existing());
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);
            writers.when(() -> SqlWriters.updateById(any(DSLContext.class), eq(SqlTables.SYS_CONFIG),
                    eq(1L), any(SysConfig.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(1L,
                    new UpdateConfigRequest("app.file.storage-dir", "存储目录", "./uploads",
                            null, "STRING", null, 1, "ACTIVE")));

            assertEquals("配置键已存在", ex.getMessage());
        }
    }

    @Test
    void normalCreateShouldNotBeSwallowedByConflictGuard() {
        ConfigService service = new ConfigService(mock(DSLContext.class));

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);

            assertDoesNotThrow(() -> service.create(createRequest()));
        }
    }

    private static SysConfig existing() {
        SysConfig c = new SysConfig();
        c.setId(1L);
        c.setConfigKey("app.file.max-bytes");
        c.setConfigName("上传大小上限");
        return c;
    }
}
