package com.acme.scaffold.common.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.acme.scaffold.common.idempotency.rules.IdempotencyRecordRules;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 幂等状态 Locale 隔离：PROCESSING 在 tr-TR 下不得解析为带点 İ。 */
class IdempotencyLocaleTest {

    private Locale originalLocale;

    @BeforeEach
    void rememberLocale() {
        originalLocale = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void processingShouldParseAndFilterUnderTurkishLocale() {
        Locale.setDefault(new Locale("tr", "TR"));
        assertEquals(IdempotencyStatus.PROCESSING, IdempotencyStatus.safeOf("processing"));
        assertEquals("PROCESSING", IdempotencyRecordRules.normalizeStatus("processing"));
        assertEquals("FAILED", IdempotencyRecordRules.normalizeStatus("failed"));
    }
}
