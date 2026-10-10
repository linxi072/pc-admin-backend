package com.acme.scaffold.security.secret;

import com.acme.scaffold.security.config.JwtProperties;

/**
 * 密钥解析辅助（纯逻辑，可单测）：将外部密钥源中的 JWT 密钥写入 {@link JwtProperties}。
 *
 * <p>仅在 {@link JwtProperties#resolveSigningKey()} 首次调用前执行（建议通过 {@code @DependsOn} 保证顺序）。
 * 仅覆盖非空结果，未配置的逻辑键保持原值（环境变量 / 随机临时密钥逻辑不变）。
 */
public final class SecretResolver {

    private SecretResolver() {
    }

    public static void resolveJwtSecrets(SecretProvider provider, JwtProperties jwt) {
        provider.get("jwt-secret").ifPresent(jwt::setJwtSecret);
        provider.get("previous-jwt-secret").ifPresent(jwt::setPreviousJwtSecret);
    }
}
