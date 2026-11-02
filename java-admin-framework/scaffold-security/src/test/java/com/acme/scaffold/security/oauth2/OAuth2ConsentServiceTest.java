package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.oauth2.OpenApiClientRules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OAuth2 用户同意应用服务")
class OAuth2ConsentServiceTest {

    private final OAuth2ConsentDataPort port = mock(OAuth2ConsentDataPort.class);
    private final OAuth2ConsentService service = new OAuth2ConsentService(port);

    @Test
    @DisplayName("无已批准同意 → isApproved=false")
    void no_consent() {
        when(port.findLatestApproved(42L, "c1")).thenReturn(Optional.empty());
        assertFalse(service.isApproved(42L, "c1", Set.of("read")));
    }

    @Test
    @DisplayName("已批准同意 scope 覆盖请求 → isApproved=true")
    void approved_covers() {
        when(port.findLatestApproved(42L, "c1")).thenReturn(Optional.of(new OAuth2ConsentInfo("read,write", null)));
        assertTrue(service.isApproved(42L, "c1", Set.of("read")));
    }

    @Test
    @DisplayName("已批准同意 scope 不足 → isApproved=false")
    void approved_insufficient() {
        when(port.findLatestApproved(42L, "c1")).thenReturn(Optional.of(new OAuth2ConsentInfo("read", null)));
        assertFalse(service.isApproved(42L, "c1", Set.of("read", "write")));
    }

    @Test
    @DisplayName("已过期同意 → isApproved=false")
    void expired() {
        when(port.findLatestApproved(42L, "c1")).thenReturn(
                Optional.of(new OAuth2ConsentInfo("read", LocalDateTime.now().minusDays(1))));
        assertFalse(service.isApproved(42L, "c1", Set.of("read")));
    }

    @Test
    @DisplayName("remember=true 记录同意（规范化 scope，顺序无关）")
    void record_remember() {
        service.record(42L, "c1", Set.of("write", "read"), true);
        ArgumentCaptor<String> scopesCaptor = ArgumentCaptor.forClass(String.class);
        verify(port).recordApproved(eq(42L), eq("c1"), scopesCaptor.capture());
        // 落库串解析后须等于请求 scope 集合（Set.of 迭代顺序不确定，故按集合而非串比较）
        assertEquals(Set.of("read", "write"), OpenApiClientRules.parseScopes(scopesCaptor.getValue()));
    }

    @Test
    @DisplayName("remember=false 不落库")
    void record_noRemember() {
        service.record(42L, "c1", Set.of("read"), false);
        verify(port, never()).recordApproved(any(), any(), any());
    }
}
