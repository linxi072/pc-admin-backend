package com.acme.scaffold.security.secret;

import java.util.Optional;

/**
 * 密钥源抽象。屏蔽「本地配置 / 外部密钥库（Vault）」差异，供 JWT 等敏感配置按需解析。
 */
public interface SecretProvider {

    /**
     * 按逻辑键取密钥原文。
     *
     * @param logicalKey 逻辑键，如 {@code jwt-secret}、{@code previous-jwt-secret}
     * @return 密钥原文；未找到或取数失败返回 {@link Optional#empty()}（不抛异常）
     */
    Optional<String> get(String logicalKey);
}
