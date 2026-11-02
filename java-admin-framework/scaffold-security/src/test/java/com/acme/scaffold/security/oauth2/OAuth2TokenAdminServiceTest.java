package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.security.oauth2.domain.OAuth2TokenDO;
import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuth2 令牌管理应用服务单元测试（M43）。
 *
 * <p>验证「管理端分页查询 / 吊销」用例对注册表的编排与视图映射，
 * 不依赖数据库（注册表以 mock 替代）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2 令牌管理应用服务")
class OAuth2TokenAdminServiceTest {

    @Mock
    private OAuth2TokenRegistry registry;

    @InjectMocks
    private OAuth2TokenAdminService service;

    private OAuth2TokenDO sampleDo(String jti, String clientId, Long userId) {
        OAuth2TokenDO t = new OAuth2TokenDO();
        t.setId(1L);
        t.setJti(jti);
        t.setClientId(clientId);
        t.setUserId(userId);
        t.setUsername(userId == null ? null : "u" + userId);
        t.setScope("read write");
        t.setTokenType(userId == null ? "CLIENT" : "USER");
        t.setIssuedAt(LocalDateTime.now());
        t.setExpiresAt(LocalDateTime.now().plusHours(1));
        t.setStatus("ACTIVE");
        return t;
    }

    @Test
    @DisplayName("分页查询活动令牌：映射为视图并按 clientId 过滤透传")
    void page_maps_and_forwards_filter() {
        PageResult<OAuth2TokenDO> repoResult = new PageResult<>(1L, 10L, 2L,
                List.of(sampleDo("j1", "c1", null), sampleDo("j2", "c1", 42L)));
        when(registry.listActive(any(PageQuery.class), eq("c1"), isNull()))
                .thenReturn(repoResult);

        PageResult<OAuth2TokenView> result = service.page(1, 10, "c1", null);

        assertEquals(2L, result.total());
        assertEquals(2, result.records().size());
        assertEquals("j1", result.records().get(0).jti());
        assertEquals("c1", result.records().get(0).clientId());
        assertEquals("CLIENT", result.records().get(0).tokenType());
        assertEquals("USER", result.records().get(1).tokenType());
        verify(registry).listActive(any(PageQuery.class), eq("c1"), isNull());
    }

    @Test
    @DisplayName("吊销：委托注册表并幂等")
    void revoke_delegates_to_registry() {
        service.revoke("jti-x");
        verify(registry).revoke("jti-x");
        // 不返回任何体，仅委托；重复调用安全（注册表幂等）
        service.revoke("jti-x");
        verify(registry, org.mockito.Mockito.times(2)).revoke("jti-x");
    }

    @Test
    @DisplayName("按 userId 过滤时透传 userId 参数")
    void page_forwards_userId_filter() {
        when(registry.listActive(any(PageQuery.class), isNull(), eq(42L)))
                .thenReturn(PageResult.empty(1, 10));
        service.page(1, 10, null, 42L);
        verify(registry).listActive(any(PageQuery.class), isNull(), eq(42L));
        verify(registry, never()).listActive(any(PageQuery.class), any(), isNull());
    }
}
