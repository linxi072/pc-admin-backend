package com.acme.scaffold.realtime.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WebSocket 握手 Origin 来源策略（CSWSH 纵深防御）。
 *
 * <p>读取 {@code app.cors.allowed-origins}（与 {@link com.acme.scaffold.config.web.CorsProperties}
 * 同源配置），供 {@link RealtimeHandshakeInterceptor} 在 JWT 校验之外再对 {@code Origin} 头做白名单校验。</p>
 *
 * <p>设计要点：本类仅依赖 Spring {@code @Value}，<b>不反向引用 {@code config.web}</b>，避免引入包级循环；
 * 来源规则的归一化与匹配为纯函数，便于离线单测。</p>
 *
 * <ul>
 *   <li>含 {@code *} 视为放开全部来源；</li>
 *   <li>{@code Origin} 为空（非浏览器 / 同源脚本 / {@code file://}）放行——CSWSH 依赖 Cookie 劫持，
 *       本系统鉴权走 {@code ?token=} 查询参数而非 Cookie，此类场景不适用，交由 JWT 校验兜底；</li>
 *   <li>具体来源支持前缀/后缀通配（如 {@code http://localhost:*}、{@code *.example.com}）。</li>
 * </ul>
 */
@Component
public class WebSocketOriginPolicy {

    private final List<String> allowedOrigins;

    public WebSocketOriginPolicy(@Value("${app.cors.allowed-origins:*}") List<String> allowedOrigins) {
        List<String> normalized = (allowedOrigins == null || allowedOrigins.isEmpty())
                ? List.of("*") : allowedOrigins;
        List<String> trimmed = normalized.stream()
                .map(s -> s.trim())
                .filter(s -> !s.isEmpty())
                .toList();
        // 全部为空字符串（如配置为 ["", ""]）时回退为放开全部来源
        this.allowedOrigins = trimmed.isEmpty() ? List.of("*") : trimmed;
    }

    /** 返回经归一化的允许来源列表（供 Spring WS 注册或调试查看）。 */
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * 判断给定 {@code Origin} 头是否允许建立握手。
     *
     * @param originHeader 请求 {@code Origin} 头（可能为空）
     * @return 允许返回 {@code true}
     */
    public boolean allows(String originHeader) {
        if (allowedOrigins.contains("*")) {
            return true;
        }
        if (originHeader == null || originHeader.isBlank()) {
            // 非浏览器 / 同源脚本无 Origin 头：CSWSH 不适用，放行交由 JWT 校验兜底
            return true;
        }
        String origin = originHeader.trim();
        for (String pattern : allowedOrigins) {
            if (matches(pattern, origin)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String pattern, String origin) {
        if (pattern.equals(origin)) {
            return true;
        }
        if (pattern.contains("*")) {
            int idx = pattern.indexOf('*');
            String prefix = pattern.substring(0, idx);
            String suffix = pattern.substring(idx + 1);
            return origin.startsWith(prefix) && origin.endsWith(suffix);
        }
        return false;
    }
}
