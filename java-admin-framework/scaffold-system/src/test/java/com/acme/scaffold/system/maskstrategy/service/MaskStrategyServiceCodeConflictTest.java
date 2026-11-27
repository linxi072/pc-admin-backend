package com.acme.scaffold.system.maskstrategy.service;

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
import com.acme.scaffold.system.maskstrategy.domain.CreateMaskStrategyRequest;
import com.acme.scaffold.system.maskstrategy.domain.UpdateMaskStrategyRequest;
import com.acme.scaffold.system.maskstrategy.domain.model.SysMaskStrategy;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;

/** 策略编码唯一键并发兜底回归：数据库冲突须转成与预校验一致的业务 409，正常路径不受影响。 */
class MaskStrategyServiceCodeConflictTest {

    private static MaskStrategyService service() {
        return new MaskStrategyService(mock(DSLContext.class));
    }

    private static CreateMaskStrategyRequest createRequest() {
        return new CreateMaskStrategyRequest("MOBILE_MASK", "手机号掩码", "mobile",
                "MOBILE", "ALL", null, 1, true, null);
    }

    private static SysMaskStrategy existing() {
        SysMaskStrategy d = new SysMaskStrategy();
        d.setId(5L);
        d.setStrategyCode("OLD_CODE");
        d.setStrategyName("旧策略");
        d.setFieldKey("mobile");
        d.setSensitiveType("MOBILE");
        d.setApplyScope("ALL");
        d.setPriority(1);
        d.setEnabled(1);
        return d;
    }

    @Test
    void concurrentCreateShouldReturnConflict() {
        MaskStrategyService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchOne(any(DSLContext.class), eq(SqlTables.MASK_STRATEGY),
                    eq(SysMaskStrategy.class), any())).thenReturn(null);
            writers.when(() -> SqlWriters.insert(any(DSLContext.class), eq(SqlTables.MASK_STRATEGY),
                    any(SysMaskStrategy.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(createRequest()));

            assertEquals("策略编码已存在", ex.getMessage());
        }
    }

    @Test
    void concurrentCodeChangeOnUpdateShouldReturnConflict() {
        MaskStrategyService service = service();

        UpdateMaskStrategyRequest req = new UpdateMaskStrategyRequest("NEW_CODE", null, null,
                null, null, null, null, null, null);

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.MASK_STRATEGY),
                    eq(SysMaskStrategy.class), eq(5L))).thenReturn(existing());
            writers.when(() -> SqlWriters.fetchOne(any(DSLContext.class), eq(SqlTables.MASK_STRATEGY),
                    eq(SysMaskStrategy.class), any())).thenReturn(null);
            writers.when(() -> SqlWriters.updateById(any(DSLContext.class), eq(SqlTables.MASK_STRATEGY),
                    eq(5L), any(SysMaskStrategy.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(5L, req));

            assertEquals("策略编码已存在", ex.getMessage());
        }
    }

    @Test
    void normalCreateShouldNotBeSwallowedByConflictGuard() {
        MaskStrategyService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchOne(any(DSLContext.class), eq(SqlTables.MASK_STRATEGY),
                    eq(SysMaskStrategy.class), any())).thenReturn(null);

            assertDoesNotThrow(() -> service.create(createRequest()));
        }
    }
}
