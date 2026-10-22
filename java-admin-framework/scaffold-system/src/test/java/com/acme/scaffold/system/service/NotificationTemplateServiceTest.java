package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.dto.CreateNotificationTemplateRequest;
import com.acme.scaffold.system.dto.NotificationTemplateView;
import com.acme.scaffold.system.dto.PreviewNotificationTemplateRequest;
import com.acme.scaffold.system.dto.UpdateNotificationTemplateRequest;
import com.acme.scaffold.system.entity.SysNotificationTemplateDO;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * {@link NotificationTemplateService} 单元测试：以 {@code mockStatic(JooqWriters, CALLS_REAL_METHODS)}
 * 作为打库替身（未桩方法走真实实现，如 {@code notDeleted} 谓词），覆盖分页映射、创建唯一性、
 * 更新部分字段、删除/启停委托、渲染预览与变量提取。不依赖数据库，离线可跑。
 */
class NotificationTemplateServiceTest {

    private final DSLContext dsl = mock(DSLContext.class);
    private NotificationTemplateService service;

    @BeforeEach
    void setUp() {
        service = new NotificationTemplateService(dsl);
    }

    private static SysNotificationTemplateDO template(Long id, String code, String content, Integer enabled) {
        SysNotificationTemplateDO d = new SysNotificationTemplateDO();
        d.setId(id);
        d.setTemplateCode(code);
        d.setTemplateName("n");
        d.setChannel("EMAIL");
        d.setTitle("t");
        d.setContent(content);
        d.setMsgType("NOTICE");
        d.setEnabled(enabled);
        return d;
    }

    @Test
    @DisplayName("page：委托 JooqWriters 并映射视图（含变量名解析）")
    void page_delegatesAndMapsViews() {
        SysNotificationTemplateDO d = template(1L, "T1", "hi ${x}", 1);
        PageResult<SysNotificationTemplateDO> raw = PageResult.of(1, 10, 1, List.of(d));
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class, CALLS_REAL_METHODS)) {
            mw.when(() -> JooqWriters.page(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(SysNotificationTemplateDO.class), any(), any(),
                    any(OrderField.class), any(OrderField.class))).thenReturn(raw);
            PageResult<NotificationTemplateView> result = service.page(1, 10, null, null, null, null, null);
            assertEquals(1, result.records().size());
            NotificationTemplateView v = result.records().get(0);
            assertEquals("T1", v.templateCode());
            assertEquals("EMAIL", v.channel());
            assertTrue(v.enabled());
            assertEquals(List.of("x"), v.variableNames());
        }
    }

    @Test
    @DisplayName("create：结构校验 + 编码唯一性，落库回填主键")
    void create_validatesAndInserts() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class, CALLS_REAL_METHODS)) {
            mw.when(() -> JooqWriters.fetchOne(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(SysNotificationTemplateDO.class), any())).thenReturn(null);
            mw.when(() -> JooqWriters.insert(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    any(SysNotificationTemplateDO.class)))
                    .thenAnswer(inv -> {
                        inv.<SysNotificationTemplateDO>getArgument(2).setId(1L);
                        return null;
                    });
            Long id = service.create(new CreateNotificationTemplateRequest(
                    "ORDER_SHIPPED", "发货通知", "EMAIL", "发货通知标题", "单号${orderNo}", "NOTICE", Boolean.TRUE, "remark"));
            assertEquals(1L, id);
            mw.verify(() -> JooqWriters.insert(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    any(SysNotificationTemplateDO.class)));
        }
    }

    @Test
    @DisplayName("create：编码重复抛出 CONFLICT")
    void create_duplicateCodeThrowsConflict() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class, CALLS_REAL_METHODS)) {
            mw.when(() -> JooqWriters.fetchOne(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(SysNotificationTemplateDO.class), any())).thenReturn(template(9L, "ORDER_SHIPPED", "x", 1));
            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(
                    new CreateNotificationTemplateRequest("ORDER_SHIPPED", "发货通知", "EMAIL", "t", "c", null, Boolean.TRUE, null)));
            assertTrue(ex.getMessage().contains("编码已存在"));
        }
    }

    @Test
    @DisplayName("create：结构非法抛出 BAD_REQUEST")
    void create_invalidStructureThrowsBadRequest() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class, CALLS_REAL_METHODS)) {
            mw.when(() -> JooqWriters.fetchOne(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(SysNotificationTemplateDO.class), any())).thenReturn(null);
            assertThrows(BusinessException.class, () -> service.create(
                    new CreateNotificationTemplateRequest("lowercase", "x", "EMAIL", "t", "c", null, Boolean.TRUE, null)));
        }
    }

    @Test
    @DisplayName("update：仅非空字段参与更新")
    void update_appliesNonNullFields() {
        SysNotificationTemplateDO existing = template(1L, "ORDER_SHIPPED", "old ${x}", 1);
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class, CALLS_REAL_METHODS)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(SysNotificationTemplateDO.class), eq(1L))).thenReturn(existing);
            // 桩掉 updateById 真实实现（否则 mock(DSLContext).update() 返回 null 触发 NPE）
            mw.when(() -> JooqWriters.updateById(any(), any(), any(), any())).thenAnswer(inv -> null);
            service.update(1L, new UpdateNotificationTemplateRequest(null, "新名称", null, null,
                    "新内容 ${y}", null, false, null));
            mw.verify(() -> JooqWriters.updateById(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(1L), any(SysNotificationTemplateDO.class)));
        }
    }

    @Test
    @DisplayName("remove：逻辑删除委托 JooqWriters.delete(logical=true)")
    void remove_logicalDelete() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class, CALLS_REAL_METHODS)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(SysNotificationTemplateDO.class), eq(1L))).thenReturn(template(1L, "T", "c", 1));
            mw.when(() -> JooqWriters.delete(any(), any(), any(), anyBoolean())).thenAnswer(inv -> null);
            service.remove(1L);
            mw.verify(() -> JooqWriters.delete(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(1L), eq(true)));
        }
    }

    @Test
    @DisplayName("toggle：启用状态更新委托 updateById")
    void toggle_updatesEnabled() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class, CALLS_REAL_METHODS)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(SysNotificationTemplateDO.class), eq(1L))).thenReturn(template(1L, "T", "c", 1));
            mw.when(() -> JooqWriters.updateById(any(), any(), any(), any())).thenAnswer(inv -> null);
            service.toggle(1L, false);
            mw.verify(() -> JooqWriters.updateById(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(1L), any(SysNotificationTemplateDO.class)));
        }
    }

    @Test
    @DisplayName("preview：已知变量渲染，未知变量列入 missing")
    void preview_rendersAndReportsMissing() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class, CALLS_REAL_METHODS)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(SysNotificationTemplateDO.class), eq(1L))).thenReturn(template(1L, "T", "hi ${x}", 1));
            Map<String, Object> filled = service.preview(1L, new PreviewNotificationTemplateRequest(Map.of("x", "v")));
            assertEquals("hi v", filled.get("rendered"));
            assertEquals(List.of(), filled.get("missingVariables"));

            Map<String, Object> empty = service.preview(1L, new PreviewNotificationTemplateRequest(Map.of()));
            assertEquals("hi ${x}", empty.get("rendered"));
            assertEquals(List.of("x"), empty.get("missingVariables"));
        }
    }

    @Test
    @DisplayName("variables：提取模板变量名")
    void variables_extractsNames() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class, CALLS_REAL_METHODS)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(SysNotificationTemplateDO.class), eq(1L))).thenReturn(template(1L, "T", "${a} ${b}", 1));
            assertEquals(List.of("a", "b"), service.variables(1L));
        }
    }

    @Test
    @DisplayName("get：不存在抛出 NOT_FOUND")
    void get_notFoundThrows() {
        try (MockedStatic<JooqWriters> mw = mockStatic(JooqWriters.class, CALLS_REAL_METHODS)) {
            mw.when(() -> JooqWriters.fetchById(any(), eq(JooqTables.SYS_NOTIFICATION_TEMPLATE),
                    eq(SysNotificationTemplateDO.class), eq(1L))).thenReturn(null);
            assertThrows(BusinessException.class, () -> service.get(1L));
        }
    }
}
