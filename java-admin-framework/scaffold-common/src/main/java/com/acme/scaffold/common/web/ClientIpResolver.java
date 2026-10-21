package com.acme.scaffold.common.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端真实 IP 解析（纯逻辑，不依赖 Spring 容器，便于离线单测）。
 *
 * <p><b>为什么不能直接取 {@code X-Forwarded-For} 首段</b>：该头由客户端任意伪造，直接采信会带来两类问题——
 * <ul>
 *   <li>限流（{@code RateLimitFilter} / {@code RateLimitAspect}）以 IP 为计数键，伪造即可无限换键绕过；</li>
 *   <li>审计日志、登录日志、在线会话记录的 IP 被污染，事后溯源失真。</li>
 * </ul>
 * 正确做法是「<b>自右向左</b>跳过已知可信的代理跳数」：完整链路自左至右为
 * {@code XFF[0], XFF[1], ..., XFF[n-1], remoteAddr}，其中右侧 {@code trustedProxyCount} 跳是运维可控的
 * 反向代理，紧邻其左的一跳才是真实客户端。</p>
 *
 * <p>可信跳数由 {@code app.web.trusted-proxy-count} 配置（默认 1，即单级 Nginx）：
 * <ul>
 *   <li>{@code 0}：完全不信任 XFF，直接取 {@code remoteAddr}（应用直连公网时的正确取值）；</li>
 *   <li>{@code 1}：单级反向代理，取 XFF 末段；</li>
 *   <li>{@code k}：k 级代理链路，取 XFF 倒数第 k 段。</li>
 * </ul>
 * 若实际链路跳数少于配置值（典型场景：客户端伪造了很长的 XFF），判定链路不可信并回落 {@code remoteAddr}，
 * 宁可解析成代理地址，也不采信伪造值。</p>
 *
 * <p><b>集成</b>：由 {@code config.web.ClientIpConfig} 注册为单例 Bean，
 * 审计切面 / 限流切面 / 限流过滤器 / 认证控制器统一注入，取代此前四处各写一遍的重复实现。</p>
 */
public final class ClientIpResolver {

    /** 事实标准代理头。 */
    public static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    /** 无 HTTP 上下文（定时任务、MQ 消费等）时的兜底值。 */
    public static final String UNKNOWN = "unknown";

    private final int trustedProxyCount;

    /**
     * @param trustedProxyCount 可信反向代理跳数；负数按 0 处理
     */
    public ClientIpResolver(int trustedProxyCount) {
        this.trustedProxyCount = Math.max(0, trustedProxyCount);
    }

    /**
     * 按实例持有的可信跳数解析客户端 IP。
     *
     * @param request 当前请求；为 {@code null}（非 HTTP 上下文）时返回 {@link #UNKNOWN}
     * @return 客户端 IP；链路不可信或解析为空时回落到 {@code remoteAddr}
     */
    public String resolve(HttpServletRequest request) {
        return resolve(request, trustedProxyCount);
    }

    /**
     * 按指定可信代理跳数解析（静态纯函数，便于单测与无 Spring 场景复用）。
     *
     * @param request           当前请求，可为 {@code null}
     * @param trustedProxyCount 可信反向代理跳数
     * @return 客户端 IP
     */
    public static String resolve(HttpServletRequest request, int trustedProxyCount) {
        if (request == null) {
            return UNKNOWN;
        }
        String remoteAddr = normalize(request.getRemoteAddr());
        if (trustedProxyCount <= 0) {
            return remoteAddr;
        }
        List<String> hops = splitHops(request.getHeader(HEADER_X_FORWARDED_FOR));
        if (hops.isEmpty()) {
            return remoteAddr;
        }
        // 链路末段是直连本服务的一跳（remoteAddr），其左 trustedProxyCount 段为可信代理
        int index = hops.size() - trustedProxyCount;
        if (index < 0) {
            return remoteAddr;
        }
        String candidate = normalize(hops.get(index));
        return candidate.isEmpty() ? remoteAddr : candidate;
    }

    /** 拆分 XFF 为跳列表：去空白、丢弃空段，保持自左至右顺序。 */
    static List<String> splitHops(String header) {
        List<String> hops = new ArrayList<>();
        if (header == null || header.isBlank()) {
            return hops;
        }
        for (String part : header.split(",")) {
            String hop = normalize(part);
            if (!hop.isEmpty()) {
                hops.add(hop);
            }
        }
        return hops;
    }

    /**
     * 归一化单跳：去首尾空白、剥 IPv6 方括号与 IPv4 尾随端口，便于直接作为限流键与日志字段。
     */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return "";
        }
        if (v.charAt(0) == '[') {
            // IPv6 带方括号：[::1] 或 [::1]:8080
            int end = v.indexOf(']');
            return end > 0 ? v.substring(1, end) : v;
        }
        int firstColon = v.indexOf(':');
        // 仅 IPv4:port 剥端口；含多个冒号的是 IPv6 原文，保持不动
        if (firstColon > 0 && v.indexOf(':', firstColon + 1) < 0) {
            return v.substring(0, firstColon);
        }
        return v;
    }
}
