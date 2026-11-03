package com.acme.scaffold.chaos;

import com.acme.scaffold.chaos.model.ChaosFault;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 混沌故障注入过滤器（R15）：在请求链路末端（接近业务处理处）按 URI 匹配已 arm 的故障规则，
 * 注入时延 / 错误 / 异常，用于韧性演练与混沌测试。
 *
 * <p><b>安全护栏</b>：仅当 {@code app.chaos.enabled=true} 时该 Bean 才被注册（{@code ChaosAutoConfiguration}
 * 的 {@code @ConditionalOnProperty} 门控）；关闭时过滤器根本不存在，请求链路零副作用。
 * 即便启用后，也仅对「已匹配且按概率抽样命中」的请求注入故障，未命中请求完全透传。</p>
 *
 * <p>排序：{@code LOWEST_PRECEDENCE - 50}，运行在绝大多数框架过滤器之后、业务处理之前，
 * 贴近「下游依赖故障」语义；不影响鉴权/追踪链路（其失败仍会被正常记录）。</p>
 */
@Order(Ordered.LOWEST_PRECEDENCE - 50)
public class ChaosFilter extends OncePerRequestFilter {

    private final ChaosRegistry registry;
    private final boolean enabled;

    public ChaosFilter(ChaosRegistry registry, ChaosProperties properties) {
        this.registry = registry;
        this.enabled = properties.isEnabled();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                   @NonNull HttpServletResponse response,
                                   @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<ChaosFault> matched = registry.match(request.getRequestURI());
        if (matched.isEmpty() || !matched.get().shouldTrigger()) {
            filterChain.doFilter(request, response);
            return;
        }
        apply(matched.get(), request, response, filterChain);
    }

    private void apply(ChaosFault fault, HttpServletRequest request,
                       HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        switch (fault.type()) {
            case LATENCY -> {
                long millis = fault.latencyMillis();
                if (millis > 0) {
                    try {
                        Thread.sleep(millis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                filterChain.doFilter(request, response);
            }
            case ERROR -> {
                response.setStatus(fault.errorStatus());
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                        "{\"code\":\"CHAOS_ERROR\",\"message\":\"injected chaos error: "
                                + fault.description() + "\"}");
                // 不调用 filterChain：短路请求，模拟依赖故障
            }
            case EXCEPTION -> {
                throw new RuntimeException("chaos injected exception: " + fault.description());
            }
        }
    }
}
