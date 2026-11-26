package com.acme.scaffold.security.token.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.DynamicMapper;
import com.acme.scaffold.mybatis.dsl.DynamicStatement;
import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.common.tenant.TenantEnforcement;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.jwt.JwtProperties;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 刷新令牌轮换并发回归：同一令牌只能成功占用一次，落败请求必须按重放吊销家族且不签发新令牌。 */
class RefreshTokenRotationConcurrencyTest {

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        TenantEnforcement.configure(null);
    }

    @Test
    void successfulRotationShouldClaimBeforeIssue() {
        FakeMapper mapper = new FakeMapper(true, false);

        String newToken = service(mapper).rotate("raw-token");

        assertNotNull(newToken);
        assertEquals(1, mapper.claimCalls.get());
        assertEquals(1, mapper.inserts.get());
        assertEquals(0, mapper.familyRevokes.get());
        assertEquals(List.of("CLAIM", "INSERT"), mapper.events);
    }

    @Test
    void lostClaimShouldRevokeFamilyAndNotIssue() {
        FakeMapper mapper = new FakeMapper(false, true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(mapper).rotate("raw-token"));

        assertEquals(SecurityErrorCode.TOKEN_REVOKED.code(), ex.getErrorCode().code());
        assertEquals(1, mapper.claimCalls.get());
        assertEquals(0, mapper.inserts.get());
        assertEquals(1, mapper.familyRevokes.get());
    }

    @Test
    void alreadyRevokedTokenShouldRevokeFamilyWithoutClaimOrIssue() {
        FakeMapper mapper = new FakeMapper(true, false);
        mapper.row.put("revoked_at", Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(mapper).rotate("raw-token"));

        assertEquals(SecurityErrorCode.TOKEN_REVOKED.code(), ex.getErrorCode().code());
        assertEquals(0, mapper.claimCalls.get());
        assertEquals(0, mapper.inserts.get());
        assertEquals(1, mapper.familyRevokes.get());
    }

    @Test
    void concurrentRotationsShouldYieldOnlyOneNewToken() throws Exception {
        FakeMapper mapper = new FakeMapper(true, false);
        RefreshTokenService service = service(mapper);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(() -> attempt(service));
            Future<Object> second = pool.submit(() -> attempt(service));

            Object a = first.get(5, TimeUnit.SECONDS);
            Object b = second.get(5, TimeUnit.SECONDS);

            assertEquals(1, List.of(a, b).stream().filter(String.class::isInstance).count());
            assertEquals(1, mapper.inserts.get());
            // 第二个请求可能在读取阶段就看到已被占用的行，也可能在条件更新阶段才落败。
            assertTrue(mapper.claimCalls.get() >= 1 && mapper.claimCalls.get() <= 2);
            assertEquals(1, mapper.familyRevokes.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private static Object attempt(RefreshTokenService service) {
        try {
            return service.rotate("raw-token");
        } catch (BusinessException ex) {
            return ex;
        }
    }

    private static RefreshTokenService service(DynamicMapper mapper) {
        JwtProperties props = mock(JwtProperties.class);
        when(props.getRefreshTokenTtl()).thenReturn(Duration.ofDays(7));
        return new RefreshTokenService(new DSLContext(mapper), props);
    }

    private static final class FakeMapper implements DynamicMapper {

        private final Map<String, Object> row = tokenRow();
        private final AtomicInteger claimCalls = new AtomicInteger();
        private final AtomicInteger inserts = new AtomicInteger();
        private final AtomicInteger familyRevokes = new AtomicInteger();
        private final List<String> events = Collections.synchronizedList(new ArrayList<>());
        private final boolean firstClaimOnly;
        private final boolean claimAlwaysFails;

        private FakeMapper(boolean firstClaimOnly, boolean claimAlwaysFails) {
            this.firstClaimOnly = firstClaimOnly;
            this.claimAlwaysFails = claimAlwaysFails;
        }

        @Override
        public List<Map<String, Object>> select(DynamicStatement statement) {
            return List.of(new LinkedHashMap<>(row));
        }

        @Override
        public Map<String, Object> selectOne(DynamicStatement statement) {
            return new LinkedHashMap<>(row);
        }

        @Override
        public int update(DynamicStatement statement) {
            List<Object> binds = statement.getBinds();
            if (binds.contains("REUSE_DETECTED")) {
                events.add("FAMILY_REVOKE");
                familyRevokes.incrementAndGet();
                return 1;
            }
            if (binds.contains("ROTATED")) {
                events.add("CLAIM");
                int call = claimCalls.getAndIncrement();
                if (claimAlwaysFails || (firstClaimOnly && call > 0)) {
                    return 0;
                }
                row.put("revoked_at", Timestamp.valueOf(LocalDateTime.now()));
                return 1;
            }
            return 1;
        }

        @Override
        public int insert(DynamicStatement statement) {
            events.add("INSERT");
            inserts.incrementAndGet();
            return 1;
        }

        @Override
        public int delete(DynamicStatement statement) {
            return 0;
        }
    }

    private static Map<String, Object> tokenRow() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 100L);
        row.put("tenant_id", 0L);
        row.put("user_id", 42L);
        row.put("client_id", "web");
        row.put("session_id", "session-1");
        row.put("family_id", "family-1");
        row.put("token_hash", "hash");
        row.put("device_id", "device-1");
        row.put("user_agent", "ua");
        row.put("ip_address", "203.0.113.10");
        row.put("issued_at", Timestamp.valueOf(now.minusMinutes(1)));
        row.put("expires_at", Timestamp.valueOf(now.plusDays(1)));
        row.put("revoked_at", null);
        row.put("revoke_reason", null);
        row.put("reuse_detected", 0);
        return row;
    }
}
