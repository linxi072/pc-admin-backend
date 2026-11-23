package com.acme.scaffold.config.gateway;

import java.util.Collection;
import java.util.Locale;

/**
 * 轻量网关 CORS 收敛纯规则（方向 B，零依赖）。
 *
 * <p>网关启用时代替分散在各控制器的 CORS 处理：校验来源白名单、识别预检请求、
 * 生成允许头。设计遵循浏览器 CORS 契约与 fail-closed：
 * <ul>
 *   <li>来源白名单精确匹配（大小写不敏感、trim），{@code *} 表示允许任意来源
 *       （但不能与「允许携带凭证」共存，遇到时按安全优先拒绝通配）；</li>
 *   <li>预检（{@code OPTIONS} + {@code Access-Control-Request-Method} 头）由网关终结，
 *       不转发到业务；</li>
 *   <li>非白名单来源返回「不允许」，由过滤器据此拒绝而非回显 Allow-Origin。</li>
 * </ul>
 *
 * <p>纯函数、无 Spring / Servlet 依赖，便于离线单测。
 */
public final class GatewayCorsRules {

    /** 预检方法。 */
    public static final String PREFLIGHT_METHOD = "OPTIONS";
    /** 请求方法头。 */
    public static final String REQUEST_METHOD_HEADER = "Access-Control-Request-Method";
    /** 请求头列表头。 */
    public static final String REQUEST_HEADERS_HEADER = "Access-Control-Request-Headers";
    /** 允许来源头。 */
    public static final String ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";
    /** 通配符来源。 */
    public static final String ANY_ORIGIN = "*";

    private GatewayCorsRules() {
    }

    /**
     * 是否为 CORS 预检请求。
     *
     * @param method             HTTP 方法
     * @param requestMethodHeader 预检请求方法头（{@code Access-Control-Request-Method}）
     * @return 方法为 OPTIONS 且带请求方法头时返回 true
     */
    public static boolean isPreflight(String method, String requestMethodHeader) {
        return method != null && PREFLIGHT_METHOD.equalsIgnoreCase(method.trim())
                && requestMethodHeader != null && !requestMethodHeader.isBlank();
    }

    /**
     * 来源是否在白名单内。
     *
     * @param origin         请求来源（可为 null）
     * @param allowedOrigins 允许的来源集合；含 "*" 表示任意来源
     * @return 命中返回 true；白名单为空/null 或来源为 null/空白返回 false（fail-closed）
     */
    public static boolean isAllowedOrigin(String origin, Collection<String> allowedOrigins) {
        if (origin == null || origin.isBlank() || allowedOrigins == null || allowedOrigins.isEmpty()) {
            return false;
        }
        String o = origin.trim().toLowerCase(Locale.ROOT);
        for (String allowed : allowedOrigins) {
            if (allowed == null || allowed.isBlank()) {
                continue;
            }
            String a = allowed.trim().toLowerCase(Locale.ROOT);
            if (ANY_ORIGIN.equals(a) || a.equals(o)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算响应应回显的 Allow-Origin 值。
     *
     * <p>白名单含 "*" 时回显 "*"；否则回显具体来源（仅当来源在白名单内）。
     * 不允许的来源返回 null，调用方据此不写该响应头（浏览器将拦截跨域响应）。</p>
     *
     * @param origin         请求来源
     * @param allowedOrigins 白名单
     * @return 允许的来源值；不允许时返回 null
     */
    public static String allowOriginValue(String origin, Collection<String> allowedOrigins) {
        if (!isAllowedOrigin(origin, allowedOrigins)) {
            return null;
        }
        String o = origin.trim();
        for (String allowed : allowedOrigins) {
            if (allowed != null && ANY_ORIGIN.equals(allowed.trim())) {
                return ANY_ORIGIN;
            }
        }
        return o;
    }
}
