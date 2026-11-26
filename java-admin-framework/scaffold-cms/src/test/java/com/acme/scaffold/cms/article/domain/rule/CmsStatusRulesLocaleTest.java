package com.acme.scaffold.cms.article.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 内容状态 Locale 隔离：PUBLISHED / OFFLINE 在 tr-TR 下仍可归一。 */
class CmsStatusRulesLocaleTest {

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
    void publishedAndOfflineShouldNormalizeUnderTurkishLocale() {
        Locale.setDefault(new Locale("tr", "TR"));
        assertEquals(CmsStatusRules.PUBLISHED, CmsStatusRules.normalize("published"));
        assertEquals(CmsStatusRules.OFFLINE, CmsStatusRules.normalize("offline"));
    }
}
