package com.acme.scaffold.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.acme.scaffold.job.domain.JobIntervalUnit;
import com.acme.scaffold.job.domain.JobTriggerType;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 定时任务枚举解析 Locale 隔离：minutes / simple 在 tr-TR 下仍可解析。 */
class JobLocaleTest {

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
    void enumsShouldParseUnderTurkishLocale() {
        Locale.setDefault(new Locale("tr", "TR"));
        assertEquals(JobIntervalUnit.MINUTES, JobService.intervalUnitOrNull("minutes"));
        assertEquals(JobTriggerType.SIMPLE, JobService.triggerTypeOrNull("simple"));
    }
}
