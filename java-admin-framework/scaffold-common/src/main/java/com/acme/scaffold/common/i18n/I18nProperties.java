package com.acme.scaffold.common.i18n;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 国际化配置：{@code app.i18n.*}。
 *
 * <ul>
 *   <li>{@code default-locale}：未携带 locale 参数时的默认语言，默认 {@code zh_CN}；</li>
 *   <li>{@code param-name}：切换语言的请求参数名，默认 {@code lang}（如 {@code ?lang=en_US}）。</li>
 * </ul>
 *
 * <p>默认配置即可工作，无需在 {@code application*.yml} 中显式声明；仅在需要覆盖时使用上述键。
 */
@ConfigurationProperties(prefix = "app.i18n")
public class I18nProperties {

    /** 默认语言：zh_CN（简体中文）。 */
    private String defaultLocale = "zh_CN";

    /** 语言切换参数名：lang。 */
    private String paramName = "lang";

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public String getParamName() {
        return paramName;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }
}
