package com.acme.scaffold.security.secret;

import java.util.Map;
import java.util.Optional;

/**
 * 本地密钥源：直接读取配置中的密钥映射（默认实现，离线可用）。
 * 实际密钥仍建议通过环境变量注入配置，而非明文写入配置文件。
 */
public class LocalSecretProvider implements SecretProvider {

    private final Map<String, String> secrets;

    public LocalSecretProvider(Map<String, String> secrets) {
        this.secrets = secrets;
    }

    @Override
    public Optional<String> get(String logicalKey) {
        return Optional.ofNullable(secrets.get(logicalKey));
    }
}
