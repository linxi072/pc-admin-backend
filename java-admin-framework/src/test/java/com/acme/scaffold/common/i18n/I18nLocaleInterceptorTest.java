package com.acme.scaffold.common.i18n;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link LocaleChangeInterceptor} + {@link SessionLocaleResolver} 协同单测：
 * 验证 {@code ?lang=xx} 能切换会话语言，缺省时回落配置默认语言。
 * 不启动 Web 上下文，仅用 Spring Mock 请求对象。
 */
class I18nLocaleInterceptorTest {

    private static final String PARAM = "lang";

    @Test
    void switchesLocaleViaParam() throws Exception {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(PARAM);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, resolver);
        request.addParameter(PARAM, "en_US");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertEquals(Locale.US, resolver.resolveLocale(request));
    }

    @Test
    void defaultsToConfiguredLocaleWhenNoParam() throws Exception {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(PARAM);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, resolver);
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertEquals(Locale.SIMPLIFIED_CHINESE, resolver.resolveLocale(request));
    }
}
