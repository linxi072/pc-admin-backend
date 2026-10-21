package com.acme.scaffold.common.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 国际化消息解析：封装 {@link MessageSource}，按当前请求语言（{@link LocaleContextHolder}）解析消息键。
 *
 * <p>约定：
 * <ul>
 *   <li>消息键统一写在 {@code i18n/messages*.properties}；调用方只传键与参数，不直接拼多语言文案；</li>
 *   <li>键缺失时返回键本身（而非抛异常），便于前端/日志定位未翻译项；</li>
 *   <li>支持 {@code {0}}、{@code {1}} 占位符参数化。</li>
 * </ul>
 */
@Service
public class I18nService {

    private final MessageSource messageSource;

    public I18nService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** 按当前线程语言解析。 */
    public String getMessage(String code, Object... args) {
        return getMessage(code, LocaleContextHolder.getLocale(), args);
    }

    /** 按指定语言解析。 */
    public String getMessage(String code, Locale locale, Object... args) {
        try {
            return messageSource.getMessage(code, args, locale);
        } catch (NoSuchMessageException e) {
            return code;
        }
    }
}
