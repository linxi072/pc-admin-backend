package com.acme.scaffold.common.sensitive;

import java.util.List;
import java.util.Locale;

/**
 * 配置键名敏感语义判定与统一掩码：供配置中心快照与系统参数管理接口共用，避免不同端点口径漂移。
 *
 * <p>判定忽略大小写与 {@code -} / {@code _} / {@code .} 分隔符；
 * {@code app.security.*} 命名空间整体视为敏感，其余键按凭证语义片段匹配。</p>
 */
public final class SensitiveConfigKeys {

    /** 敏感配置统一替换为该掩码，既不回显值也能保留键名供审计。 */
    public static final String MASKED_VALUE = "******";

    private static final String SECURITY_NAMESPACE = "app.security.";

    private static final List<String> SENSITIVE_FRAGMENTS = List.of(
            "secret", "password", "passwd", "pwd", "privatekey", "apikey", "accesskey",
            "credential", "keystore", "authorization", "bearer", "token", "webhook");

    private SensitiveConfigKeys() {
    }

    /** 判断配置键是否含密钥/口令语义；null 键按非敏感处理。 */
    public static boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith(SECURITY_NAMESPACE)) {
            return true;
        }
        String normalized = lower.replaceAll("[^a-z0-9]", "");
        return SENSITIVE_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    /** 敏感键返回固定掩码，其它键原样返回。 */
    public static String mask(String key, String value) {
        return isSensitive(key) ? MASKED_VALUE : value;
    }
}
