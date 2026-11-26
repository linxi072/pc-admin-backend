package com.acme.scaffold.system.online.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.DynamicMapper;
import com.acme.scaffold.mybatis.dsl.DynamicStatement;
import com.acme.scaffold.security.jwt.JwtProperties;
import com.acme.scaffold.security.token.service.RefreshTokenService;
import com.acme.scaffold.security.token.service.TokenVersionService;
import com.acme.scaffold.system.online.domain.RestrictLoginRequest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 强制下线/限制登录必须递增 tokenVersion，防止客户端省略或伪造 X-Device-Id 继续使用旧令牌。 */
class OnlineKickTokenVersionTest {

    @Test
    void forceLogoutShouldBumpTokenVersion() {
        CapturingMapper mapper = new CapturingMapper();
        mapper.selectResults.add(List.of(sessionRow()));
        TokenVersionService tokenVersions = mock(TokenVersionService.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        OnlineUserService service = onlineUserService(mapper, tokenVersions, refreshTokens);

        int affected = service.forceLogout(List.of(10L));

        assertEquals(1, affected);
        verify(refreshTokens).revokeByUserAndDevice(42L, "device-1");
        verify(tokenVersions).bumpUsers(List.of(42L));
    }

    @Test
    void markOfflineByUserShouldBumpTokenVersion() {
        CapturingMapper mapper = new CapturingMapper();
        TokenVersionService tokenVersions = mock(TokenVersionService.class);
        OnlineUserService service = onlineUserService(mapper, tokenVersions, mock(RefreshTokenService.class));

        service.markOfflineByUser(42L, OnlineUserService.REASON_LOGOUT);

        verify(tokenVersions).bumpUsers(List.of(42L));
    }

    @Test
    void restrictShouldBumpTokenVersion() {
        CapturingMapper mapper = new CapturingMapper();
        mapper.selectResults.add(List.of(userRow()));
        mapper.selectResults.add(List.of());
        TokenVersionService tokenVersions = mock(TokenVersionService.class);
        SecurityContextFacade securityContext = mock(SecurityContextFacade.class);
        when(securityContext.getCurrentPrincipal()).thenReturn(Optional.empty());
        LoginRestrictionService service = new LoginRestrictionService(new DSLContext(mapper), securityContext,
                mock(RefreshTokenService.class), tokenVersions);

        Long restrictionId = service.restrict(42L, new RestrictLoginRequest(30, "测试限制"));

        assertNotNull(restrictionId);
        verify(tokenVersions).bumpUsers(List.of(42L));
    }

    private static OnlineUserService onlineUserService(DynamicMapper mapper, TokenVersionService tokenVersions,
                                                       RefreshTokenService refreshTokens) {
        return new OnlineUserService(new DSLContext(mapper), refreshTokens, tokenVersions,
                mock(SecurityContextFacade.class), mock(JwtProperties.class), mock(LoginRestrictionService.class));
    }

    private static Map<String, Object> sessionRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 10L);
        row.put("tenant_id", 0L);
        row.put("user_id", 42L);
        row.put("device_id", "device-1");
        row.put("client_id", "web");
        row.put("username", "alice");
        row.put("display_name", "Alice");
        row.put("login_ip", "203.0.113.10");
        row.put("status", "ONLINE");
        return row;
    }

    private static Map<String, Object> userRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 42L);
        row.put("tenant_id", 0L);
        row.put("username", "alice");
        row.put("display_name", "Alice");
        row.put("status", "ACTIVE");
        row.put("token_version", 1);
        row.put("deleted", 0);
        return row;
    }

    private static final class CapturingMapper implements DynamicMapper {

        private final Deque<List<Map<String, Object>>> selectResults = new ArrayDeque<>();
        private final List<DynamicStatement> updates = new ArrayList<>();

        @Override
        public List<Map<String, Object>> select(DynamicStatement statement) {
            List<Map<String, Object>> next = selectResults.poll();
            return next == null ? List.of() : new ArrayList<>(next);
        }

        @Override
        public Map<String, Object> selectOne(DynamicStatement statement) {
            List<Map<String, Object>> next = selectResults.poll();
            return next == null || next.isEmpty() ? null : new LinkedHashMap<>(next.get(0));
        }

        @Override
        public int update(DynamicStatement statement) {
            updates.add(statement);
            return 1;
        }

        @Override
        public int insert(DynamicStatement statement) {
            return 1;
        }

        @Override
        public int delete(DynamicStatement statement) {
            return 0;
        }
    }
}
