package com.acme.scaffold;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类：拉起真实 Spring 上下文（随机端口），使用 {@code integration} profile
 * 连接本机 MySQL（不使用 Docker）。所有集成测试打 {@code integration} 标签，
 * 沙箱内可用 {@code -Dtest=... -Dgroups='!integration'} 排除，避免无 MySQL 时误跑。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Tag("integration")
public abstract class BaseIntegrationTest {

    @LocalServerPort
    protected int port;

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * 每个测试类独立的假客户端 IP（由类名哈希稳定生成，同类多次调用结果一致）。
     *
     * <p>背景：{@code RateLimitFilter} 以「login:IP」为 key 按 60s 窗口限流 10 次，
     * 且过滤器为单例。多个 IT 类共享同一 Spring 上下文（context caching）时登录计数
     * 跨类累计，连续跑全部 IT 会触发 429。{@code X-Forwarded-For} 被
     * {@code RateLimitFilter.clientIp()} 优先采用，各测试类在登录请求携带独立 IP
     * 即可隔离计数，无需改动生产代码或降低限流阈值。
     */
    protected String clientIp() {
        int h = getClass().getName().hashCode();
        return "10." + ((h >>> 24) & 0x7f) + "." + ((h >>> 16) & 0xff) + "." + ((h >>> 8) & 0xff);
    }
}
