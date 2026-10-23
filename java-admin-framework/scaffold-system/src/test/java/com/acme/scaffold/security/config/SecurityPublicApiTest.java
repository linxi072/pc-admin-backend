package com.acme.scaffold.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全白名单守护测试（真实联调回归）：{@code BASE_PUBLIC_API} 必须放行联通探针端点
 * 与 M15 通知退订端点。
 *
 * <p>背景：{@code ConnectivityController} 的 ping/slow 设计为零数据库依赖的匿名联通探测
 * （前端 {@code http.js} 探测与 Python 探针脚本均无 token），但白名单遗漏了这两个端点，
 * 导致真实运行环境下探针请求被 Spring Security 拦截为 401 空体（BearerTokenAuthenticationEntryPoint），
 * 而非预期的统一包络 {@code code=='0'}。既有 ConnectivityControllerTest 以 standalone MockMvc
 * 装配、不经安全过滤器链，故未暴露——本测试以白名单常量反射断言补齐该盲区。</p>
 *
 * <p>M15 退订端点（{@code GET /api/notification/unsubscribe}）同类：邮件正文链接由外部
 * 浏览器点击，无法出示 JWT，鉴权由令牌 HMAC 签名承担——白名单遗漏同样会把它拦成 401 空体，
 * 用户点击退订链接得到的是错误页而非退订结果。</p>
 *
 * <p>注意：{@code AntPathRequestMatcher} 的 {@code urlPathHelper} 默认为 null，其
 * {@code getRequestPath} 回退读取 {@code servletPath + pathInfo}；而 MockHttpServletRequest
 * 的 servletPath 默认为空串（真实 Tomcat 下 DispatcherServlet 映射为 / 时 servletPath
 * 即完整 URI）。故构造请求须显式 {@code setServletPath}，否则一切匹配均为 false。</p>
 */
@DisplayName("安全白名单：联通探针与退订端点必须匿名可访问")
class SecurityPublicApiTest {

    /** 构造模拟真实容器语义的请求：servletPath 即完整请求 URI（DispatcherServlet 映射 / 时）。 */
    private static MockHttpServletRequest get(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setServletPath(uri);
        return request;
    }

    private static MockHttpServletRequest post(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setServletPath(uri);
        return request;
    }

    private static RequestMatcher basePublicApi() throws Exception {
        Field field = SecurityConfig.class.getDeclaredField("BASE_PUBLIC_API");
        field.setAccessible(true);
        return (RequestMatcher) field.get(null);
    }

    @Test
    @DisplayName("ping / slow GET 命中白名单（联调契约：匿名可访问）")
    void connectivityProbeEndpoints_permitted() throws Exception {
        RequestMatcher matcher = basePublicApi();
        assertTrue(matcher.matches(get("/api/connectivity/ping")));
        assertTrue(matcher.matches(get("/api/connectivity/slow")));
    }

    @Test
    @DisplayName("退订端点 GET 命中白名单（M15：邮件正文链接匿名可点击）")
    void unsubscribeEndpoint_permitted() throws Exception {
        assertTrue(basePublicApi().matches(get("/api/notification/unsubscribe")));
    }

    @Test
    @DisplayName("反向守护：探针/退订端点仅放行 GET，业务接口不得被误放行")
    void nonPermittedRequests_notMatched() throws Exception {
        RequestMatcher matcher = basePublicApi();
        // 探针端点只开放读语义，POST 不得命中
        assertFalse(matcher.matches(post("/api/connectivity/ping")));
        assertFalse(matcher.matches(post("/api/connectivity/slow")));
        // 退订端点同理：仅链接点击的 GET 语义，不开放 POST 批量面
        assertFalse(matcher.matches(post("/api/notification/unsubscribe")));
        // 典型业务接口仍须鉴权
        assertFalse(matcher.matches(get("/api/system/users")));
        assertFalse(matcher.matches(get("/api/monitoring/alerts/inbox")));
        // 子路径不得被前缀误伤（AntPathRequestMatcher 精确匹配语义）
        assertFalse(matcher.matches(get("/api/connectivity/ping/extra")));
        assertFalse(matcher.matches(get("/api/notification/unsubscribe/extra")));
    }
}
