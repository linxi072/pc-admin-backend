package com.acme.scaffold.config.center;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 配置快照脱敏：白名单前缀之外的键不会进入快照，但对于已进入快照的敏感键统一掩码。
 *
 * <p>前缀白名单（如 {@code app.*}）只能挡住系统属性 / 环境变量等非应用配置，
 * 不能挡住 {@code app.security.jwt-secret}、{@code app.security.oauth2.private-key}、
 * {@code app.xxx.password} 等同属应用命名空间但含密钥语义的配置。这里对键名做宽容匹配
 * （忽略大小写、忽略 {@code -} / {@code _} / {@code .} 分隔），命中即返回固定掩码，不回显原值。</p>
 */
public final class ConfigSnapshotSanitizer {

    /** 敏感配置统一替换为该掩码，既不回显值也能保留键名供审计。 */
    public static final String MASKED_VALUE = "******";

    private static final String SECURITY_NAMESPACE = "app.security.";

    private static final List<String> SENSITIVE_FRAGMENTS = List.of(
            "secret", "password", "passwd", "pwd", "privatekey", "apikey", "accesskey",
            "credential", "keystore", "authorization", "bearer", "token");

    private ConfigSnapshotSanitizer() {
    }

    /** 判断配置键是否含密钥/口令语义；null 键按非敏感处理。 */
    public static boolean isSensitiveKey(String key) {
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
        return isSensitiveKey(key) ? MASKED_VALUE : value;
    }

    /** 返回脱敏后的新 Map，不修改入参，保持原有遍历顺序。 */
    public static Map<String, String> sanitize(Map<String, String> source) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : source.entrySet()) {
            result.put(e.getKey(), mask(e.getKey(), e.getValue()));
        }
        return result;
    }
}
