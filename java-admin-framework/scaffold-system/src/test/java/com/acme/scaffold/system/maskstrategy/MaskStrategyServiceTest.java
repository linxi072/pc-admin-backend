package com.acme.scaffold.system.maskstrategy;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.system.maskstrategy.dto.CreateMaskStrategyRequest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link MaskStrategyService} 离线单测：入参结构校验与「先全额校验再落库」守卫。
 *
 * <p>DB 交互路径（查询/写入/缓存失效）按项目约定由 MySQL 集成测试覆盖——mock jOOQ 链式 API 既脆弱
 * 又偏离真实实现；此处仅固化不触库的校验语义：脱敏类型/作用范围合法性、空集合防御，
 * 以及校验失败时<b>零数据库交互</b>（避免「前几项写成功、后一项非法」的半成品状态）。</p>
 */
class MaskStrategyServiceTest {

    private DSLContext dsl;
    private MaskStrategyService service;

    @BeforeEach
    void setUp() {
        dsl = mock(DSLContext.class);
        service = new MaskStrategyService(dsl);
    }

    @Test
    @DisplayName("create：非法脱敏类型拒绝（白名单外）")
    void create_rejectsInvalidSensitiveType() {
        CreateMaskStrategyRequest req = new CreateMaskStrategyRequest(
                "USER_MOBILE", "用户手机", "sys_user.mobile", "PHONE", "ROLE_EXEMPT", null, 0, true, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("脱敏类型非法"), ex.getMessage());
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("create：非法作用范围拒绝")
    void create_rejectsInvalidScope() {
        CreateMaskStrategyRequest req = new CreateMaskStrategyRequest(
                "USER_MOBILE", "用户手机", "sys_user.mobile", "MOBILE", "PARTIAL", null, 0, true, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("作用范围非法"), ex.getMessage());
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("create：优先级越界拒绝")
    void create_rejectsPriorityOutOfRange() {
        CreateMaskStrategyRequest req = new CreateMaskStrategyRequest(
                "USER_MOBILE", "用户手机", "sys_user.mobile", "MOBILE", "ALL", null, 10000, true, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("优先级"), ex.getMessage());
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("create：编码过长拒绝（长度上限 64）")
    void create_rejectsOverlongCode() {
        CreateMaskStrategyRequest req = new CreateMaskStrategyRequest(
                "X".repeat(65), "用户手机", "sys_user.mobile", "MOBILE", "ALL", null, 0, true, null);
        assertThrows(BusinessException.class, () -> service.create(req));
        verifyNoInteractions(dsl);
    }

    // ------------------------------------------------------------------
    // 脱敏效果预览（#751，复用 SensitiveMasker.mask 单一数据源）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("previewMask：按脱敏类型返回掩码结果，且不触库")
    void previewMask_masksByType() {
        String masked = service.previewMask("MOBILE", "13812345678");
        assertEquals("138****5678", masked, "预览必须与运行时脱敏口径一致");
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("previewMask：脱敏类型大小写不敏感")
    void previewMask_caseInsensitiveType() {
        String masked = service.previewMask("mobile", "13812345678");
        assertEquals("138****5678", masked);
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("previewMask：证件文本按键名抹除凭证值")
    void previewMask_credentialText() {
        String masked = service.previewMask("CREDENTIAL_TEXT",
                "{\"password\":\"secret123\",\"mobile\":\"13812345678\"}");
        assertTrue(masked.contains("\"password\":\"******\""), masked);
        assertTrue(masked.contains("138****5678"), masked);
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("previewMask：空示例值原样返回（不抛异常）")
    void previewMask_blankSampleReturnsAsIs() {
        assertNull(service.previewMask("MOBILE", null));
        assertEquals("", service.previewMask("MOBILE", ""));
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("previewMask：非法脱敏类型拒绝（400）且不触库")
    void previewMask_rejectsInvalidType() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.previewMask("PHONE", "13812345678"));
        assertTrue(ex.getMessage().contains("脱敏类型非法"), ex.getMessage());
        verifyNoInteractions(dsl);
    }
}
