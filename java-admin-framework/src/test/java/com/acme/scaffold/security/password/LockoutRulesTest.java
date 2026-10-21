package com.acme.scaffold.security.password;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockoutRulesTest {

    private static final int MAX = 5;
    private static final Duration BASE = Duration.ofMinutes(15);
    private static final Duration CAP = Duration.ofHours(24);

    @Test
    void shouldLock_atAndAboveThreshold() {
        assertFalse(LockoutRules.shouldLock(4, MAX));
        assertTrue(LockoutRules.shouldLock(5, MAX));
        assertTrue(LockoutRules.shouldLock(20, MAX));
    }

    @Test
    void computeDuration_zeroBeforeThreshold() {
        assertEquals(Duration.ZERO, LockoutRules.computeDuration(4, MAX, BASE, CAP));
    }

    @Test
    void computeDuration_firstTierIsBase() {
        assertEquals(BASE, LockoutRules.computeDuration(5, MAX, BASE, CAP));
        assertEquals(BASE, LockoutRules.computeDuration(9, MAX, BASE, CAP));
    }

    @Test
    void computeDuration_progressiveDoubling() {
        assertEquals(Duration.ofMinutes(30), LockoutRules.computeDuration(10, MAX, BASE, CAP));
        assertEquals(Duration.ofHours(1), LockoutRules.computeDuration(15, MAX, BASE, CAP));
        assertEquals(Duration.ofHours(2), LockoutRules.computeDuration(20, MAX, BASE, CAP));
        assertEquals(Duration.ofHours(4), LockoutRules.computeDuration(25, MAX, BASE, CAP));
    }

    @Test
    void computeDuration_cappedAtMax() {
        assertEquals(CAP, LockoutRules.computeDuration(40, MAX, BASE, CAP));
        assertEquals(CAP, LockoutRules.computeDuration(100, MAX, BASE, CAP));
    }
}
