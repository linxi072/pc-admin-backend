package com.acme.scaffold.system.openapi;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.system.openapi.dto.CreateOpenApiClientRequest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link OpenApiClientService} 离线单测：注册入参结构校验与「先全额校验再落库」守卫。
 *
 * <p>DB 交互路径（唯一性查询/写入/轮换更新）按项目约定由 MySQL 集成测试覆盖；此处仅固化
 * 不触库的校验语义：client_id 格式、scope 合法性、过期时间，以及校验失败时<b>零数据库交互</b>
 * （密钥哈希器同理不被调用——绝不生成「校验没过却已编码密钥」的中间态）。</p>
 */
class OpenApiClientServiceTest {

    private DSLContext dsl;
    private PasswordEncoder passwordEncoder;
    private OpenApiClientService service;

    @BeforeEach
    void setUp() {
        dsl = mock(DSLContext.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new OpenApiClientService(dsl, passwordEncoder);
    }

    @Test
    @DisplayName("create：client_id 格式非法拒绝")
    void create_rejectsInvalidClientId() {
        CreateOpenApiClientRequest req = new CreateOpenApiClientRequest(
                "bad", "合作伙伴系统", "read", "ENABLED", null, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("客户端 ID"), ex.getMessage());
        verifyNoInteractions(dsl, passwordEncoder);
    }

    @Test
    @DisplayName("create：scope 单项非法拒绝")
    void create_rejectsInvalidScope() {
        CreateOpenApiClientRequest req = new CreateOpenApiClientRequest(
                "acme-partner-01", "合作伙伴系统", "Read_Write", "ENABLED", null, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("scope 非法"), ex.getMessage());
        verifyNoInteractions(dsl, passwordEncoder);
    }

    @Test
    @DisplayName("create：过期时间早于当前拒绝")
    void create_rejectsPastExpiry() {
        CreateOpenApiClientRequest req = new CreateOpenApiClientRequest(
                "acme-partner-01", "合作伙伴系统", "read", "ENABLED",
                LocalDateTime.now().minusDays(1), null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("过期时间"), ex.getMessage());
        verifyNoInteractions(dsl, passwordEncoder);
    }

    @Test
    @DisplayName("create：状态非法拒绝")
    void create_rejectsInvalidStatus() {
        CreateOpenApiClientRequest req = new CreateOpenApiClientRequest(
                "acme-partner-01", "合作伙伴系统", "read", "PAUSED", null, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("状态非法"), ex.getMessage());
        verifyNoInteractions(dsl, passwordEncoder);
    }
}
