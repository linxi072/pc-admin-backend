package com.acme.scaffold.common.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.idempotency.domain.IdempotencyRecord;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.DynamicMapper;
import com.acme.scaffold.mybatis.dsl.DynamicStatement;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 幂等 FAILED 重试并发回归：状态跃迁必须 CAS，只有一个重试者能进入 PROCESSING。 */
class IdempotencyFailedRetryConcurrencyTest {

    @Test
    void failedRetryShouldUseStatusCasUpdate() {
        CapturingMapper mapper = new CapturingMapper(1);
        mapper.selectResults.add(failed("fp"));
        IdempotencyService service = service(mapper);

        IdempotencyService.AcquireResult result = service.acquire("key", "scope", 1L, "fp", 60);

        assertEquals(IdempotencyAction.PROCEED, result.action());
        DynamicStatement claimUpdate = mapper.updates.get(0);
        assertEquals(true, claimUpdate.getBinds().contains(IdempotencyStatus.FAILED.name()),
                "FAILED→PROCESSING 的 UPDATE 必须带旧状态比较条件");
    }

    @Test
    void lostCasRaceShouldNotProceed() {
        CapturingMapper mapper = new CapturingMapper(0);
        mapper.selectResults.add(failed("fp"));
        mapper.selectResults.add(withStatus(IdempotencyStatus.PROCESSING.name(), "fp", null));
        IdempotencyService service = service(mapper);

        IdempotencyService.AcquireResult result = service.acquire("key", "scope", 1L, "fp", 60);

        assertEquals(IdempotencyAction.CONFLICT, result.action());
    }

    @Test
    void lostCasRaceWithSuccessShouldReplaySnapshot() {
        CapturingMapper mapper = new CapturingMapper(0);
        mapper.selectResults.add(failed("fp"));
        mapper.selectResults.add(withStatus(IdempotencyStatus.SUCCESS.name(), "fp", "{\"ok\":1}"));
        IdempotencyService service = service(mapper);

        IdempotencyService.AcquireResult result = service.acquire("key", "scope", 1L, "fp", 60);

        assertEquals(IdempotencyAction.REPLAY, result.action());
        assertEquals("{\"ok\":1}", result.responseSnapshot());
    }

    @Test
    void lostCasRaceWithFailedAgainShouldStillConflict() {
        CapturingMapper mapper = new CapturingMapper(0);
        mapper.selectResults.add(failed("fp"));
        mapper.selectResults.add(failed("fp"));
        IdempotencyService service = service(mapper);

        IdempotencyService.AcquireResult result = service.acquire("key", "scope", 1L, "fp", 60);

        assertEquals(IdempotencyAction.CONFLICT, result.action(),
                "CAS 失败即使重查仍为 FAILED，也不能再放行一次");
    }

    private static IdempotencyService service(CapturingMapper mapper) {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new IdempotencyService(new DSLContext(mapper), new ObjectMapper(), transactionManager);
    }

    private static IdempotencyRecord failed(String fingerprint) {
        return withStatus(IdempotencyStatus.FAILED.name(), fingerprint, null);
    }

    private static IdempotencyRecord withStatus(String status, String fingerprint, String snapshot) {
        IdempotencyRecord r = new IdempotencyRecord();
        r.setId(11L);
        r.setIdempotencyKey("key");
        r.setApiScope("scope");
        r.setUserId(1L);
        r.setRequestFingerprint(fingerprint);
        r.setStatus(status);
        r.setResponseSnapshot(snapshot);
        r.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        return r;
    }

    private static final class CapturingMapper implements DynamicMapper {

        private final Deque<IdempotencyRecord> selectResults = new ArrayDeque<>();
        private final List<DynamicStatement> updates = new ArrayList<>();
        private final int updateRows;

        private CapturingMapper(int updateRows) {
            this.updateRows = updateRows;
        }

        @Override
        public List<Map<String, Object>> select(DynamicStatement statement) {
            Map<String, Object> row = toRow(selectResults.poll());
            return row == null ? List.of() : List.of(row);
        }

        @Override
        public Map<String, Object> selectOne(DynamicStatement statement) {
            return toRow(selectResults.poll());
        }

        @Override
        public int update(DynamicStatement statement) {
            updates.add(statement);
            return updateRows;
        }

        @Override
        public int insert(DynamicStatement statement) {
            return 1;
        }

        @Override
        public int delete(DynamicStatement statement) {
            return 1;
        }

        private static Map<String, Object> toRow(IdempotencyRecord r) {
            if (r == null) {
                return null;
            }
            return Map.of(
                    "id", r.getId(),
                    "idempotency_key", r.getIdempotencyKey(),
                    "api_scope", r.getApiScope(),
                    "user_id", r.getUserId(),
                    "request_fingerprint", r.getRequestFingerprint(),
                    "status", r.getStatus(),
                    "response_snapshot", r.getResponseSnapshot() == null ? "" : r.getResponseSnapshot(),
                    "expires_at", r.getExpiresAt());
        }
    }
}
