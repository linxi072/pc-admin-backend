package com.acme.scaffold.common.i18n;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

/**
 * 国际化装配：声明 {@link MessageSource}（资源束 {@code i18n/messages}）、
 * {@link SessionLocaleResolver}（按会话记忆语言）与 {@link LocaleChangeInterceptor}
 * （通过 {@code ?lang=xx} 切换），并把拦截器注册到 Web 拦截器链。
 *
 * <p>设计要点：
 * <ul>
 *   <li>资源束统一放在 {@code src/main/resources/i18n/}，框架级错误走 {@code messages}、
 *       Bean Validation 约束消息走 {@code validation}，均按 {@code _zh_CN} / {@code _en_US}
 *       两级回退，新增语言只需补一个对应 {@code .properties}；</li>
 *   <li>编码固定 UTF-8，资源束可安全存中文（{@link ReloadableResourceBundleMessageSource} 经 Spring
 *       PropertiesPersister 读取，规避 JDK {@code ResourceBundle} 默认 ISO-8859-1 的乱码坑）；</li>
 *   <li>默认语言与切换参数经 {@link I18nProperties} 配置化，缺省 zh_CN / lang；</li>
 *   <li>以 {@code @ConditionalOnMissingBean} 允许测试或上层覆盖。</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(I18nProperties.class)
public class I18nConfig implements WebMvcConfigurer {

    /** 资源束基名：框架级错误消息（{@code i18n/messages}）与 Bean Validation 约束消息（{@code i18n/validation}）。 */
    private static final String MESSAGE_BASENAME = "classpath:i18n/messages";
    private static final String VALIDATION_BASENAME = "classpath:i18n/validation";

    private final LocaleChangeInterceptor localeChangeInterceptor;

    public I18nConfig(LocaleChangeInterceptor localeChangeInterceptor) {
        this.localeChangeInterceptor = localeChangeInterceptor;
    }

    @Bean
    @ConditionalOnMissingBean(MessageSource.class)
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames(MESSAGE_BASENAME, VALIDATION_BASENAME);
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setCacheSeconds(3600);
        return source;
    }

    @Bean
    @ConditionalOnMissingBean(LocaleResolver.class)
    public LocaleResolver localeResolver(I18nProperties properties) {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(parseLocale(properties.getDefaultLocale()));
        return resolver;
    }

    /**
     * {@link LocaleChangeInterceptor} 声明。<b>必须为 {@code static}</b>：本类构造注入该 Bean，
     * 实例级 {@code @Bean} 工厂需先完成配置类实例化，会形成「i18nConfig → 自身」循环依赖
     * （Spring Boot 3 默认禁止，启动即失败）；static 工厂不依赖配置类实例，环即解开。
     */
    @Bean
    @ConditionalOnMissingBean(LocaleChangeInterceptor.class)
    public static LocaleChangeInterceptor localeChangeInterceptor(I18nProperties properties) {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(properties.getParamName());
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor).order(0);
    }

    /**
     * 解析 {@code zh_CN}/{@code en_US}/{@code zh-CN}/{@code en-US} 等写法为 {@link Locale}。
     */
    static Locale parseLocale(String value) {
        if (value == null || value.isBlank()) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        String normalized = value.trim().replace('-', '_');
        String[] parts = normalized.split("_", 3);
        String language = parts[0];
        String country = parts.length > 1 ? parts[1] : "";
        String variant = parts.length > 2 ? parts[2] : "";
        return new Locale(language, country, variant);
    }
}
