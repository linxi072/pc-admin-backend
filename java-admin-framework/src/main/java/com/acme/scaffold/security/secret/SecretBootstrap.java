package com.acme.scaffold.security.secret;

import com.acme.scaffold.security.config.JwtProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 外部密钥引导：在 vault 模式下，于应用启动早期从 Vault 解析 JWT 密钥并写入 {@link JwtProperties}，
 * 使后续 {@code JwtDecoderConfig} 构建解码器时使用真实密钥（而非随机临时密钥）。
 *
 * <p>默认 provider=local：本方法为空操作，不接入任何外部密钥库，行为完全向后兼容。
 * 通过 {@code JwtDecoderConfig.jwtDecoder()} 上的 {@code @DependsOn("secretBootstrap")} 保证
 * 密钥解析先于解码器构建完成。
 */
@Configuration
@EnableConfigurationProperties(SecretProperties.class)
public class SecretBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SecretBootstrap.class);

    private final SecretProperties props;
    private final JwtProperties jwtProperties;

    public SecretBootstrap(SecretProperties props, JwtProperties jwtProperties) {
        this.props = props;
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    public void resolveExternalSecrets() {
        if (!"vault".equalsIgnoreCase(props.getProvider())) {
            log.debug("secret.provider={}，使用本地密钥源（默认），不接入外部密钥库", props.getProvider());
            return;
        }
        SecretProvider provider = new VaultSecretProvider(
                props.getVault().getAddress(),
                props.getVault().getToken(),
                props.getVault().getPaths());
        SecretResolver.resolveJwtSecrets(provider, jwtProperties);
        log.info("已从 Vault({}) 解析 JWT 密钥源", props.getVault().getAddress());
    }
}
