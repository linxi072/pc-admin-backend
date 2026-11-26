package com.acme.scaffold.admin.config;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 生产环境外部密钥缺失 fail-fast 守卫。
 *
 * <p>JwtProperties 与 RsaKeyProvider 在密钥未配置时会生成仅当前进程有效的临时密钥，
 * 该行为适合开发/测试，但生产部署若漏配环境变量，服务仍会「正常」启动：重启后管理员会话全部失效，
 * OAuth2 JWKS 的 kid 变化导致客户端令牌无法跨重启校验，问题只在重启后暴露。</p>
 *
 * <p>仅在 {@code prod} profile 生效，校验 {@code JWT_SECRET} 与 {@code APP_OAUTH2_PRIVATE_KEY}
 * 对应的配置键非空；缺少任一项即拒绝启动。特殊部署可显式设置
 * {@code app.security.secrets.guard.enabled=false} 临时关闭，但不推荐。</p>
 */
@Slf4j
@Component
@Profile("prod")
public class ProductionSecretsGuard implements ApplicationRunner {

    static final String ENABLED_PROPERTY = "app.security.secrets.guard.enabled";
    static final String JWT_SECRET_PROPERTY = "app.security.jwt-secret";
    static final String OAUTH2_PRIVATE_KEY_PROPERTY = "app.security.oauth2.private-key";

    private final Environment environment;

    public ProductionSecretsGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        verify();
    }

    /** 公开入口便于契约测试用 {@code MockEnvironment} 直接驱动，无需启动 Spring 上下文。 */
    public void verify() {
        if (!Boolean.TRUE.equals(environment.getProperty(ENABLED_PROPERTY, Boolean.class, Boolean.TRUE))) {
            log.warn("生产外部密钥校验已关闭（{}=false），临时密钥将在重启后失效，请自行确认风险",
                    ENABLED_PROPERTY);
            return;
        }
        List<String> missing = missingSecrets();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "生产环境缺少必需的外部密钥配置：" + String.join("、", missing)
                            + "。请在部署环境中注入对应变量；如确需临时使用进程内临时密钥，"
                            + "可设置 " + ENABLED_PROPERTY + "=false 关闭本校验（不推荐）。");
        }
        log.info("生产外部密钥配置校验通过：JWT_SECRET 与 APP_OAUTH2_PRIVATE_KEY 均已配置");
    }

    private List<String> missingSecrets() {
        List<String> missing = new ArrayList<>(2);
        if (!StringUtils.hasText(environment.getProperty(JWT_SECRET_PROPERTY))) {
            missing.add("JWT_SECRET（" + JWT_SECRET_PROPERTY + "）");
        }
        if (!StringUtils.hasText(environment.getProperty(OAUTH2_PRIVATE_KEY_PROPERTY))) {
            missing.add("APP_OAUTH2_PRIVATE_KEY（" + OAUTH2_PRIVATE_KEY_PROPERTY + "）");
        }
        return missing;
    }
}
