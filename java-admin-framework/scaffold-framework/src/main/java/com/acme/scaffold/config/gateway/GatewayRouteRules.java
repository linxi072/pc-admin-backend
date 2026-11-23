package com.acme.scaffold.config.gateway;

import java.util.Locale;

/**
 * 轻量网关路由分类纯规则（方向 B，零依赖自研网关层）。
 *
 * <p>把入站请求 URI 归为四类，供前置过滤器决定「放行 / 限流 / 内网准入 / 直通」，
 * 与 {@code PLATFORM-EVOLUTION-PLAN.md} B2 路由表口径一致：
 * <ul>
 *   <li>{@code API}：{@code /api/**}，业务 API，纳入网关限流与 traceId 注入；</li>
 *   <li>{@code WEBSOCKET}：{@code /ws/**}（含 {@code /websocket/**}），WebSocket 握手，
 *       需 upgrade 透传，不重复做 body 类处理；</li>
 *   <li>{@code ACTUATOR}：{@code /actuator/**}（含 {@code /livez} / {@code /readyz}），
 *       仅内网准入，公网来源直接拒绝；</li>
 *   <li>{@code OTHER}：静态资源、OpenAPI 文档、登录页等，直通不过网关限流。</li>
 * </ul>
 *
 * <p>纯函数、无 Spring / Servlet 依赖，便于离线单测；过滤器仅消费本类的判定结果。
 * URI 规范化：去查询串与 fragment、忽略大小写前缀匹配、压缩重复斜杠，避免
 * {@code /API//actuator} 之类的绕过。
 */
public final class GatewayRouteRules {

    private static final String API_PREFIX = "/api/";
    private static final String API_EXACT = "/api";
    private static final String WS_PREFIX = "/ws/";
    private static final String WS_ALT_PREFIX = "/websocket/";
    private static final String ACTUATOR_PREFIX = "/actuator/";
    private static final String ACTUATOR_EXACT = "/actuator";

    private GatewayRouteRules() {
    }

    /** 路由类别。 */
    public enum RouteType {
        /** 业务 API。 */
        API,
        /** WebSocket 握手。 */
        WEBSOCKET,
        /** 运维端点（仅内网）。 */
        ACTUATOR,
        /** 其它（静态资源 / 文档等），直通。 */
        OTHER
    }

    /**
     * 规范化 URI：裁剪查询串与 fragment、转小写、压缩重复斜杠并保证以 {@code /} 开头。
     * null / 空返回空串。
     */
    public static String normalize(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "";
        }
        String s = uri;
        int cut = s.length();
        int q = s.indexOf('?');
        if (q >= 0) {
            cut = Math.min(cut, q);
        }
        int h = s.indexOf('#');
        if (h >= 0) {
            cut = Math.min(cut, h);
        }
        s = s.substring(0, cut).toLowerCase(Locale.ROOT);
        // 压缩重复斜杠
        StringBuilder sb = new StringBuilder(s.length());
        boolean prevSlash = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/') {
                if (prevSlash) {
                    continue;
                }
                prevSlash = true;
            } else {
                prevSlash = false;
            }
            sb.append(c);
        }
        String out = sb.toString();
        return out.startsWith("/") ? out : "/" + out;
    }

    /** 按规范化 URI 判定路由类别。 */
    public static RouteType classify(String uri) {
        String s = normalize(uri);
        if (ACTUATOR_EXACT.equals(s) || s.startsWith(ACTUATOR_PREFIX)
                || "/livez".equals(s) || "/readyz".equals(s)) {
            return RouteType.ACTUATOR;
        }
        if (s.startsWith(WS_PREFIX) || s.startsWith(WS_ALT_PREFIX)) {
            return RouteType.WEBSOCKET;
        }
        if (API_EXACT.equals(s) || s.startsWith(API_PREFIX)) {
            return RouteType.API;
        }
        return RouteType.OTHER;
    }

    /**
     * 是否应纳入网关限流：仅业务 API 与 WebSocket 握手计入，
     * actuator（低频运维）与静态资源直通，避免探针心跳污染限流计数。
     */
    public static boolean shouldRateLimit(String uri) {
        RouteType type = classify(uri);
        return type == RouteType.API || type == RouteType.WEBSOCKET;
    }

    /**
     *  actuator 端点是否允许该客户端 IP 访问。
     *
     * @param uri            请求 URI
     * @param clientIp       解析出的客户端真实 IP（已由 ClientIpResolver 处理代理链）
     * @param internalCidrs  允许的内网网段前缀列表（CIDR 简化为前缀匹配，如 "10." / "192.168." / "127."）
     * @return true=允许；非 actuator 一律放行；actuator 需 clientIp 命中任一内网前缀
     */
    public static boolean allowActuator(String uri, String clientIp, java.util.List<String> internalCidrs) {
        if (classify(uri) != RouteType.ACTUATOR) {
            return true;
        }
        if (clientIp == null || clientIp.isBlank() || internalCidrs == null || internalCidrs.isEmpty()) {
            return false; // fail-closed：内网白名单未配置时，actuator 一律拒绝
        }
        for (String prefix : internalCidrs) {
            if (prefix != null && !prefix.isBlank() && clientIp.startsWith(prefix.trim())) {
                return true;
            }
        }
        return false;
    }
}
