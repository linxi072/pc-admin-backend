package com.acme.scaffold.security.antireplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReplayProtectionRulesTest {

    private static final long SERVER_NOW = 1_700_000_000_000L;
    private static final long MAX_SKEW = 5_000L;

    @Test
    void withinSkew_isOk() {
        assertEquals(ReplayProtectionRules.TimestampVerdict.OK,
                ReplayProtectionRules.verifyTimestamp(SERVER_NOW + 1000L, SERVER_NOW, MAX_SKEW));
        assertEquals(ReplayProtectionRules.TimestampVerdict.OK,
                ReplayProtectionRules.verifyTimestamp(SERVER_NOW - 1000L, SERVER_NOW, MAX_SKEW));
        assertEquals(ReplayProtectionRules.TimestampVerdict.OK,
                ReplayProtectionRules.verifyTimestamp(SERVER_NOW, SERVER_NOW, MAX_SKEW));
    }

    @Test
    void missing_isNull() {
        assertEquals(ReplayProtectionRules.TimestampVerdict.MISSING,
                ReplayProtectionRules.verifyTimestamp(null, SERVER_NOW, MAX_SKEW));
    }

    @Test
    void invalid_isNonPositive() {
        assertEquals(ReplayProtectionRules.TimestampVerdict.INVALID,
                ReplayProtectionRules.verifyTimestamp(0L, SERVER_NOW, MAX_SKEW));
        assertEquals(ReplayProtectionRules.TimestampVerdict.INVALID,
                ReplayProtectionRules.verifyTimestamp(-1L, SERVER_NOW, MAX_SKEW));
    }

    @Test
    void tooOld_exceedsSkewEarlier() {
        assertEquals(ReplayProtectionRules.TimestampVerdict.TOO_OLD,
                ReplayProtectionRules.verifyTimestamp(SERVER_NOW - 10_000L, SERVER_NOW, MAX_SKEW));
    }

    @Test
    void tooFuture_exceedsSkewLater() {
        assertEquals(ReplayProtectionRules.TimestampVerdict.TOO_FUTURE,
                ReplayProtectionRules.verifyTimestamp(SERVER_NOW + 10_000L, SERVER_NOW, MAX_SKEW));
    }
}
