package com.acme.scaffold.common.config;

import java.util.Optional;

/**
 * 系统变量读取端口（E 阶段配置归口）：把散落在 {@code @Value} 默认值里的硬编码配置统一收口到
 * 「系统变量」（sys_config）功能，业务代码优先读系统变量，缺省回退代码内默认值，保证零回归。
 *
 * <p>依赖倒置：端口定义在 scaffold-common，实现在 scaffold-system（委托 ConfigService），
 * 避免下层模块反向依赖 system 破坏模块 DAG。未注册实现时（如单测/裁剪部署），
 * {@code SystemConfigs} 静态工具回退到 code default。</p>
 */
public interface SystemConfigPort {

    /** 读取系统变量原始字符串；不存在/停用/空值时返回 empty。 */
    Optional<String> get(String key);

    /** 读取字符串，系统变量缺失时返回默认值。 */
    default String getString(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    /** 读取整数，系统变量缺失或非法时返回默认值。 */
    default int getInt(String key, int defaultValue) {
        String v = get(key).orElse(null);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 读取长整数，系统变量缺失或非法时返回默认值。 */
    default long getLong(String key, long defaultValue) {
        String v = get(key).orElse(null);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 读取布尔，系统变量缺失时返回默认值；仅 "true"/"false"（忽略大小写）生效。 */
    default boolean getBoolean(String key, boolean defaultValue) {
        String v = get(key).orElse(null);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(v.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(v.trim())) {
            return false;
        }
        return defaultValue;
    }
}
