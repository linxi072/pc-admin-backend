package com.acme.scaffold.workbench.report.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.acme.scaffold.workbench.report.domain.ReportType;
import java.lang.reflect.Method;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 报表导出参数解析 Locale 隔离：api_call 在 tr-TR 下不得回落默认 USAGE。 */
class ReportExportTaskLocaleTest {

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
    void reportTypeShouldParseUnderTurkishLocale() throws Exception {
        Locale.setDefault(new Locale("tr", "TR"));
        Method parseEnum = ReportExportTask.class.getDeclaredMethod(
                "parseEnum", String.class, Class.class, Enum.class);
        parseEnum.setAccessible(true);

        Object result = parseEnum.invoke(null, "api_call", ReportType.class, ReportType.USAGE);

        assertEquals(ReportType.API_CALL, result);
    }
}
