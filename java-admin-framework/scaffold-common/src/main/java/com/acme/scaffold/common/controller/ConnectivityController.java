package com.acme.scaffold.common.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.ConnectivityProbe;
import com.acme.scaffold.common.api.dto.ConnectivitySlowProbe;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.i18n.I18nService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * 超时探针默认延迟（毫秒）。
     *
     * <p>必须大于联通探针默认客户端超时阈值（{@code connectivity-spec.json} 的 2000ms），
     * 确保以默认参数请求即可触发<b>真实</b>的客户端超时链路（socket read timeout），
     * 而不是等到服务端响应。</p>
     */
    static final int DEFAULT_DELAY_MS = 5000;

    /**
     * 超时探针最大延迟（毫秒）。
     *
     * <p>上限保护：慢端点会占用容器请求线程，延迟可被任意指定时有线程耗尽风险，
     * 故收敛到 10s 以内——既足够触发任何合理超时阈值，又将单请求资源占用限制在可控范围。</p>
     */
    static final int MAX_DELAY_MS = 10000;

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

    /**
     * 超时探针端点：延迟指定毫秒后返回统一包络，专供联通测试验证<b>真实</b>超时链路。
     *
     * <p>背景：{@code connectivity-spec.json} 的 timeoutEndpoint 指向本路径，此前真实后端
     * 并未实现该端点，探针打真实后端只会得到 404 而非超时，「真实 504 链路」无法闭环。
     * 现在以默认 5000ms 延迟请求（探针默认阈值 2000ms）即可在客户端触发真实
     * {@code TimeoutError}/{@code ECONNABORTED}，与网关 504 语义对齐。</p>
     *
     * <p>与 {@link #ping} 一样零数据库依赖、匿名可访问（属探针端点，不承载业务数据）；
     * 防滥用依靠 {@link #MAX_DELAY_MS} 上限而非鉴权。</p>
     *
     * @param delayMs 延迟毫秒数，可缺省；非数字返回 400，超出范围按上限收敛（见 {@link #resolveDelayMs}）
     */
    @GetMapping("/slow")
    public Result<ConnectivitySlowProbe> slow(@RequestParam(required = false) String delayMs) throws InterruptedException {
        int resolved = resolveDelayMs(delayMs);
        if (resolved > 0) {
            Thread.sleep(resolved);
        }
        return Result.success(new ConnectivitySlowProbe(resolved));
    }

    /**
     * 解析超时探针延迟参数（纯函数，便于离线单测与规则复用）：
     * <ul>
     *   <li>{@code null} / 空白 → 默认 {@link #DEFAULT_DELAY_MS}；</li>
     *   <li>可解析整数 → 按 {@code [0, MAX_DELAY_MS]} 收敛（负值归零，超限取上限）；</li>
     *   <li>非数字 → {@link BusinessException#badRequest}（400 / COMMON_001）。</li>
     * </ul>
     */
    static int resolveDelayMs(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_DELAY_MS;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw BusinessException.badRequest("delayMs 必须为 0-10000 之间的整数（毫秒）");
        }
        return Math.min(Math.max(parsed, 0), MAX_DELAY_MS);
    }
}
