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
}
