package com.acme.scaffold.common.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link I18nConfig} 装配级单测：以 {@link ApplicationContextRunner} 起轻量上下文，
 * 验证配置类可正常装配（无循环依赖）且三个 Bean 齐备。
 *
 * <p>背景：I18nConfig 曾构造注入 {@code LocaleChangeInterceptor}，而该 Bean 声明在配置类自身
 * （实例级 {@code @Bean} 工厂），形成「i18nConfig → 自身」循环依赖——纯函数单测无法暴露，
 * 仅启动/上下文加载时失败。修复为 static {@code @Bean} 工厂后，本测试以最小上下文锁住该行为，
 * 防止回归。</p>
 */
class I18nConfigAssemblerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(I18nConfig.class);

    @Test
    @DisplayName("I18nConfig 可完整装配：无循环依赖，MessageSource / LocaleResolver / LocaleChangeInterceptor 齐备")
    void assemblesWithoutCircularReference() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(org.springframework.context.MessageSource.class);
            assertThat(context).hasSingleBean(org.springframework.web.servlet.LocaleResolver.class);
            assertThat(context).hasSingleBean(org.springframework.web.servlet.i18n.LocaleChangeInterceptor.class);
            assertThat(context).hasSingleBean(I18nProperties.class);
        });
    }

    @Test
    @DisplayName("默认语言缺省 zh_CN：localeResolver 以配置默认值构造")
    void defaultLocaleFallsBackToZhCn() throws Exception {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var resolver = (org.springframework.web.servlet.i18n.SessionLocaleResolver)
                    context.getBean(org.springframework.web.servlet.LocaleResolver.class);
            // AbstractLocaleResolver#getDefaultLocale 为 protected，测试经反射读取构造值
            var field = org.springframework.web.servlet.i18n.AbstractLocaleResolver.class
                    .getDeclaredField("defaultLocale");
            field.setAccessible(true);
            assertThat(field.get(resolver)).isEqualTo(java.util.Locale.SIMPLIFIED_CHINESE);
        });
    }
}
