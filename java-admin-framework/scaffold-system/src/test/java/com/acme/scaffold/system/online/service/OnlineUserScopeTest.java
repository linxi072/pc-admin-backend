package com.acme.scaffold.system.online.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.DynamicMapper;
import com.acme.scaffold.mybatis.dsl.DynamicStatement;
import com.acme.scaffold.security.jwt.JwtProperties;
import com.acme.scaffold.security.permission.DataScopeContext;
import com.acme.scaffold.security.permission.DataScopeResult;
import com.acme.scaffold.security.token.service.RefreshTokenService;
import com.acme.scaffold.security.token.service.TokenVersionService;
import com.acme.scaffold.system.online.domain.OnlineUserQuery;
import com.acme.scaffold.system.online.domain.RestrictLoginRequest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 在线管理数据权限回归：分页/强退按用户范围过滤，限制目标越权按 NOT_FOUND 处理。 */
class OnlineUserScopeTest {

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    void pageShouldFilterSessionsBySelfScope() {
        selfScope();
        CapturingMapper mapper = new CapturingMapper();
        LoginRestrictionService restrictions = mock(LoginRestrictionService.class);
        when(restrictions.activeRestrictedUserIds(any())).thenReturn(Set.of());
        OnlineUserService service = service(mapper, restrictions);

        service.page(new OnlineUserQuery(1, 20, null, null, null));

        assertTrue(mapper.statements.stream()
                .anyMatch(s -> s.getBinds().contains(7L) && s.rawSql().contains("user_id")));
    }

    @Test
    void forceLogoutShouldApplyScopeConditionToTargetSessions() {
        selfScope();
        CapturingMapper mapper = new CapturingMapper();
        mapper.selectRows = List.of(sessionRow());
        OnlineUserService service = service(mapper, mock(LoginRestrictionService.class));

        service.forceLogout(List.of(10L));

        assertTrue(mapper.statements.stream()
                .anyMatch(s -> s.rawSql().contains("sys_online_session") && s.getBinds().contains(7L)));
    }

    @Test
    void restrictShouldRejectTargetOutsideScope() {
        selfScope();
        CapturingMapper mapper = new CapturingMapper();
        LoginRestrictionService restrictions = mock(LoginRestrictionService.class);
        OnlineUserService service = service(mapper, restrictions);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.restrict(8L, new RestrictLoginRequest(30, "测试")));

        assertEquals("COMMON_404", ex.getErrorCode().code());
        verify(restrictions, never()).restrict(any(), any());
    }

    private static void selfScope() {
        DataScopeContext.set(new DataScopeContext.Scope(7L, DataScopeResult.self()));
    }

    private static OnlineUserService service(DynamicMapper mapper, LoginRestrictionService restrictions) {
        return new OnlineUserService(new DSLContext(mapper), mock(RefreshTokenService.class),
                mock(TokenVersionService.class), mock(SecurityContextFacade.class), mock(JwtProperties.class),
                restrictions);
    }

    private static Map<String, Object> sessionRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 10L);
        row.put("user_id", 7L);
        row.put("device_id", "device-1");
        row.put("username", "alice");
        row.put("status", "ONLINE");
        return row;
    }

    private static final class CapturingMapper implements DynamicMapper {

        private final List<DynamicStatement> statements = new ArrayList<>();
        private final Deque<Map<String, Object>> selectOneResults = new ArrayDeque<>();
        private List<Map<String, Object>> selectRows = List.of();

        @Override
        public List<Map<String, Object>> select(DynamicStatement statement) {
            statements.add(statement);
            return new ArrayList<>(selectRows);
        }

        @Override
        public Map<String, Object> selectOne(DynamicStatement statement) {
            statements.add(statement);
            Map<String, Object> next = selectOneResults.poll();
            if (next != null) {
                return next;
            }
            return Map.of("0", 0);
        }

        @Override
        public int update(DynamicStatement statement) {
            statements.add(statement);
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
