package com.acme.scaffold.security.token.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.Field;
import com.acme.scaffold.mybatis.dsl.Record1;
import com.acme.scaffold.mybatis.dsl.Table;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** TokenVersion 缓存回填竞态回归：回源期间发生强退 bump，旧版本不得写回缓存。 */
class TokenVersionCacheFillRaceTest {

    @Test
    void staleFillAfterInvalidationShouldBeDiscarded() {
        TokenVersionCache cache = new TokenVersionCache(30_000L);
        long before = cache.generation();

        cache.invalidate(Set.of(42L));
        cache.putIfUnchanged(42L, 1, before);

        assertNotEquals(before, cache.generation());
        assertNull(cache.get(42L));
    }

    @Test
    void clearShouldAdvanceGenerationAndDiscardStaleFill() {
        TokenVersionCache cache = new TokenVersionCache(30_000L);
        cache.put(42L, 1);
        long before = cache.generation();

        cache.clear();
        cache.putIfUnchanged(42L, 1, before);

        assertNotEquals(before, cache.generation());
        assertNull(cache.get(42L));
    }

    @Test
    void sameGenerationFillShouldBeStored() {
        TokenVersionCache cache = new TokenVersionCache(30_000L);
        long generation = cache.generation();

        cache.putIfUnchanged(42L, 2, generation);

        assertEquals(2, cache.get(42L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void serviceShouldNotCacheVersionLoadedBeforeBump() {
        DSLContext dsl = mock(DSLContext.class, RETURNS_DEEP_STUBS);
        Record1<Integer> oldRow = mock(Record1.class);
        Record1<Integer> newRow = mock(Record1.class);
        when(oldRow.value1()).thenReturn(1);
        when(newRow.value1()).thenReturn(2);
        when(dsl.select(any(Field.class)).from(any(Table.class)).where(any(Condition.class)).fetchOne())
                .thenReturn(oldRow, newRow);

        RaceCache cache = new RaceCache();
        TokenVersionService service = new TokenVersionService(
                dsl, mock(TokenVersionDataPort.class), cache);

        assertEquals(1, service.currentVersion(42L));
        assertNull(cache.get(42L), "回源期间发生 bump，旧版本不得回填");
        assertEquals(2, service.currentVersion(42L), "下一次校验必须回源拿到递增后的版本");
    }

    /** 模拟「读库拿到旧版本 → 强退 bump → 回填」的交错。 */
    private static final class RaceCache extends TokenVersionCache {

        private RaceCache() {
            super(30_000L);
        }

        @Override
        public void putIfUnchanged(Long userId, Integer version, long expectedGeneration) {
            super.invalidate(Set.of(userId));
            super.putIfUnchanged(userId, version, expectedGeneration);
        }
    }
}
