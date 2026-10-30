package com.acme.scaffold.common.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link I18nService} 单测：覆盖多语言解析、线程语言、默认束回退、缺失键兜底与占位符插值。
 * 直接构造 {@link ReloadableResourceBundleMessageSource} 读取 {@code i18n/messages*} 资源束，
 * 不依赖 Spring 上下文，保证离线可编译可运行。
 */
class I18nServiceTest {

    private I18nService buildService() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setCacheSeconds(-1);
        return new I18nService(source);
    }

    @AfterEach
    void clear() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void resolvesChineseByExplicitLocale() {
        I18nService svc = buildService();
        assertEquals("资源不存在", svc.getMessage("error.not_found", Locale.SIMPLIFIED_CHINESE));
    }

    @Test
    void resolvesEnglishByExplicitLocale() {
        I18nService svc = buildService();
        assertEquals("Resource not found", svc.getMessage("error.not_found", Locale.US));
    }

    @Test
    void resolvesViaThreadLocale() {
        I18nService svc = buildService();
        LocaleContextHolder.setLocale(Locale.US);
        assertEquals("Resource not found", svc.getMessage("error.not_found"));
    }

    @Test
    void fallsBackToDefaultBundleWhenLocaleSpecificMissing() {
        I18nService svc = buildService();
        // error.only_default 仅存在于默认 messages.properties，en_US 束缺失 -> 回退默认束（中文）
        assertEquals("仅默认语言文案", svc.getMessage("error.only_default", Locale.US));
    }

    @Test
    void returnsCodeWhenKeyMissing() {
        I18nService svc = buildService();
        assertEquals("no.such.key", svc.getMessage("no.such.key", Locale.US));
    }

    @Test
    void interpolatesArguments() {
        I18nService svc = buildService();
        assertEquals("字段 email 校验失败",
                svc.getMessage("error.field_invalid", Locale.SIMPLIFIED_CHINESE, "email"));
        assertEquals("Field email validation failed",
                svc.getMessage("error.field_invalid", Locale.US, "email"));
    }
}
