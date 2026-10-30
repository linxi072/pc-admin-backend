package com.acme.scaffold.security.password;

import com.acme.scaffold.system.entity.SysPasswordHistoryDO;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHistoryRulesTest {

    /** 明文即密文的测试用匹配器（聚焦规则逻辑，不引入 BCrypt 随机盐）。 */
    private static final PasswordEncoder RAW = new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
            return raw.toString();
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            return raw.toString().equals(encoded);
        }

        @Override
        public boolean upgradeEncoding(String encoded) {
            return false;
        }
    };

    @Test
    void reused_whenMatchesAnyRecentHash() {
        List<String> recent = List.of("old1", "old2", "old3");
        assertTrue(PasswordHistoryRules.isReused("old2", recent, RAW));
        assertFalse(PasswordHistoryRules.isReused("fresh", recent, RAW));
    }

    @Test
    void notReused_whenHistoryEmptyOrNull() {
        assertFalse(PasswordHistoryRules.isReused("x", null, RAW));
        assertFalse(PasswordHistoryRules.isReused("x", List.of(), RAW));
        assertFalse(PasswordHistoryRules.isReused(null, List.of("a"), RAW));
    }

    @Test
    void idsToPrune_keepsMostRecentLimit() {
        List<SysPasswordHistoryDO> all = List.of(
                h(1L, LocalDateTime.of(2024, 1, 1, 0, 0)),
                h(2L, LocalDateTime.of(2024, 1, 2, 0, 0)),
                h(3L, LocalDateTime.of(2024, 1, 3, 0, 0)),
                h(4L, LocalDateTime.of(2024, 1, 4, 0, 0)),
                h(5L, LocalDateTime.of(2024, 1, 5, 0, 0)),
                h(6L, LocalDateTime.of(2024, 1, 6, 0, 0)),
                h(7L, LocalDateTime.of(2024, 1, 7, 0, 0)));
        // limit=5：保留最近 5 条（3,4,5,6,7），删除最旧的 1,2
        assertEquals(List.of(2L, 1L), PasswordHistoryRules.idsToPrune(all, 5));
    }

    @Test
    void idsToPrune_noPruneWhenWithinLimit() {
        List<SysPasswordHistoryDO> all = List.of(
                h(1L, LocalDateTime.of(2024, 1, 1, 0, 0)),
                h(2L, LocalDateTime.of(2024, 1, 2, 0, 0)));
        assertEquals(List.of(), PasswordHistoryRules.idsToPrune(all, 5));
        assertEquals(List.of(), PasswordHistoryRules.idsToPrune(null, 5));
    }

    private static SysPasswordHistoryDO h(Long id, LocalDateTime t) {
        SysPasswordHistoryDO d = new SysPasswordHistoryDO();
        d.setId(id);
        d.setCreatedAt(t);
        return d;
    }
}
