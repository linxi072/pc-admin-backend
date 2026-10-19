package com.acme.scaffold.common.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.ConnectivityProbe;
import com.acme.scaffold.common.i18n.I18nService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * 联通探针端点：极轻量、零数据库依赖，专供前端健康检查与前后端联通测试使用。
 *
 * <p>设计要点：
 * <ul>
 *   <li>不依赖任何业务模块/数据库，后端未完全就绪时也能响应，适合作为「链路是否通」的首个探针；</li>
 *   <li>通过 {@code LocaleContextHolder} 回显后端实际解析到的语言，验证多语言协商链路；</li>
 *   <li>返回统一 {@link Result} 包络，字段对齐前端 {@code src/api/connectivity.js}。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/connectivity")
public class ConnectivityController {

    /** 框架标识，联通测试断言固定值。 */
    static final String FRAMEWORK = "java-admin-framework";

    private final I18nService i18n;

    public ConnectivityController(I18nService i18n) {
        this.i18n = i18n;
    }

    @GetMapping("/ping")
    public Result<ConnectivityProbe> ping(HttpServletRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        // 顺带验证 i18n 链路可用：解析一个已知键，异常时会记日志但不影响联通判定。
        i18n.getMessage("result.ok");
        return Result.success(new ConnectivityProbe(
                Instant.now(),
                locale.toLanguageTag(),
                UUID.randomUUID().toString(),
                FRAMEWORK));
    }
}
