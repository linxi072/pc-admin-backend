package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.system.tenant.dto.CreateTenantRequest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link TenantService} 离线单测：注册入参结构校验与「先全额校验再落库」守卫。
 *
 * <p>DB 交互路径（唯一性查询/写入/更新/启停）按项目约定由 MySQL 集成测试覆盖；此处仅固化
 * 不触库的校验语义：tenant_code 格式、状态白名单、联系人电话/邮箱格式、过期时间，以及校验失败时
 * <b>零数据库交互</b>（绝不生成「校验没过却已落库」的中间态）。</p>
 */
class TenantServiceTest {

    private DSLContext dsl;
    private TenantService service;

    @BeforeEach
    void setUp() {
        dsl = mock(DSLContext.class);
        service = new TenantService(dsl);
    }

    @Test
    @DisplayName("create：tenant_code 格式非法拒绝")
    void create_rejectsInvalidTenantCode() {
        CreateTenantRequest req = new CreateTenantRequest(
                "bad", "示例租户", null, null, null, "ENABLED", null, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("租户编码"), ex.getMessage());
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("create：状态非法拒绝")
    void create_rejectsInvalidStatus() {
        CreateTenantRequest req = new CreateTenantRequest(
                "ACME01", "示例租户", null, null, null, "PAUSED", null, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("状态非法"), ex.getMessage());
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("create：过期时间早于当前拒绝")
    void create_rejectsPastExpiry() {
        CreateTenantRequest req = new CreateTenantRequest(
                "ACME01", "示例租户", null, null, null, "ENABLED",
                LocalDateTime.now().minusDays(1), null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("过期时间"), ex.getMessage());
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("create：联系人邮箱格式非法拒绝")
    void create_rejectsInvalidEmail() {
        CreateTenantRequest req = new CreateTenantRequest(
                "ACME01", "示例租户", "张三", null, "not-an-email", "ENABLED", null, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("邮箱"), ex.getMessage());
        verifyNoInteractions(dsl);
    }

    @Test
    @DisplayName("create：联系人电话格式非法拒绝")
    void create_rejectsInvalidPhone() {
        CreateTenantRequest req = new CreateTenantRequest(
                "ACME01", "示例租户", "张三", "abc", null, "ENABLED", null, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("电话"), ex.getMessage());
        verifyNoInteractions(dsl);
    }
}
