package com.acme.scaffold.system.notifymgmt.service;

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
import com.acme.scaffold.system.notifymgmt.domain.CreateNotificationTemplateRequest;
import com.acme.scaffold.system.notifymgmt.domain.UpdateNotificationTemplateRequest;
import com.acme.scaffold.system.notifymgmt.domain.model.SysNotificationTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;

/** 模板编码唯一键并发兜底回归：数据库冲突须转成与预校验一致的业务 409，正常路径不受影响。 */
class NotificationTemplateServiceCodeConflictTest {

    private static NotificationTemplateService service() {
        return new NotificationTemplateService(mock(DSLContext.class));
    }

    private static CreateNotificationTemplateRequest createRequest() {
        return new CreateNotificationTemplateRequest("TASK_RESULT_NOTIFY", "任务结果通知",
                "IN_APP", "任务完成", "任务 ${taskName} 已完成", null, true, null);
    }

    private static SysNotificationTemplate existing() {
        SysNotificationTemplate d = new SysNotificationTemplate();
        d.setId(11L);
        d.setTemplateCode("OLD_CODE");
        d.setTemplateName("旧模板");
        d.setChannel("IN_APP");
        d.setContent("旧内容");
        d.setMsgType("NOTICE");
        d.setEnabled(1);
        return d;
    }

    @Test
    void concurrentCreateShouldReturnConflict() {
        NotificationTemplateService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchOne(any(DSLContext.class), eq(SqlTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(SysNotificationTemplate.class), any())).thenReturn(null);
            writers.when(() -> SqlWriters.insert(any(DSLContext.class), eq(SqlTables.SYS_NOTIFICATION_TEMPLATE),
                    any(SysNotificationTemplate.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(createRequest()));

            assertEquals("模板编码已存在", ex.getMessage());
        }
    }

    @Test
    void concurrentCodeChangeOnUpdateShouldReturnConflict() {
        NotificationTemplateService service = service();

        UpdateNotificationTemplateRequest req = new UpdateNotificationTemplateRequest("NEW_CODE",
                null, null, null, null, null, null, null);

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(SysNotificationTemplate.class), eq(11L))).thenReturn(existing());
            writers.when(() -> SqlWriters.fetchOne(any(DSLContext.class), eq(SqlTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(SysNotificationTemplate.class), any())).thenReturn(null);
            writers.when(() -> SqlWriters.updateById(any(DSLContext.class), eq(SqlTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(11L), any(SysNotificationTemplate.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(11L, req));

            assertEquals("模板编码已存在", ex.getMessage());
        }
    }

    @Test
    void normalCreateShouldNotBeSwallowedByConflictGuard() {
        NotificationTemplateService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchOne(any(DSLContext.class), eq(SqlTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(SysNotificationTemplate.class), any())).thenReturn(null);

            assertDoesNotThrow(() -> service.create(createRequest()));
        }
    }
}
