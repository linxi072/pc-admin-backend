package com.acme.scaffold.common.config;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 系统变量静态入口：供无法注入 Bean 的字段/静态工具在"使用处"动态读取系统变量。
 *
 * <p>{@link SystemConfigPortImpl}（scaffold-system）启动时经 {@link #bind} 注册实现；
 * 未绑定时（单测 / 裁剪部署 / 启动早期）所有读取回退到调用方提供的 code default，零回归。</p>
 */
public final class SystemConfigs {

    private static final AtomicReference<SystemConfigPort> PORT = new AtomicReference<>();

    private SystemConfigs() {
    }

    /** 由 system 模块的实现 Bean 在初始化时绑定（重复绑定以最后一次为准）。 */
    public static void bind(SystemConfigPort port) {
        PORT.set(port);
    }

    /** 解绑（仅测试用）。 */
    public static void unbind() {
        PORT.set(null);
    }

    public static Optional<String> get(String key) {
        SystemConfigPort port = PORT.get();
        return port == null ? Optional.empty() : port.get(key);
    }

    public static String getString(String key, String defaultValue) {
        SystemConfigPort port = PORT.get();
        return port == null ? defaultValue : port.getString(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        SystemConfigPort port = PORT.get();
        return port == null ? defaultValue : port.getInt(key, defaultValue);
    }

    public static long getLong(String key, long defaultValue) {
        SystemConfigPort port = PORT.get();
        return port == null ? defaultValue : port.getLong(key, defaultValue);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        SystemConfigPort port = PORT.get();
        return port == null ? defaultValue : port.getBoolean(key, defaultValue);
    }
}
