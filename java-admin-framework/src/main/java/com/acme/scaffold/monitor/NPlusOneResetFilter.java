package com.acme.scaffold.monitor;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 请求级 N+1 探测状态清理：在每个请求进入时重置 {@link NPlusOneProbe} 的线程局部计数，
 * 确保探测仅跨「单个请求」累计，避免线程池复用导致的计数跨请求串台。
 *
 * <p>作为 Spring Bean 的 {@link Filter} 会被自动注册为全局过滤器（对所有请求生效）。
 */
@Component
@RequiredArgsConstructor
public class NPlusOneResetFilter implements Filter {

    private final NPlusOneProbe probe;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        probe.reset();
        chain.doFilter(request, response);
    }
}
