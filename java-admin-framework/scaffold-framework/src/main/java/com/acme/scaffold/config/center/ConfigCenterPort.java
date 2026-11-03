package com.acme.scaffold.config.center;

import java.util.Map;
import java.util.Optional;

/**
 * 配置中心端口（ROADMAP R16 · 防腐层）。
 *
 * <p>定义「配置读取 / 快照 / 刷新 / 动态刷新能力」的抽象，使业务侧只依赖本端口，
 * 不感知底层是本地 {@code Environment}、Nacos 还是 Apollo。具体实现由 {@code scaffold-framework} 装配，
 * 离线环境默认提供 {@link LocalConfigCenterAdapter}（读取 Spring Environment），
 * Nacos/Apollo 等远端实现由外部 jar（参照 R1 插件机制）提供，避免向离线仓库引入其 SDK 依赖。</p>
 */
public interface ConfigCenterPort {

    /** 配置中心类型标识（如 "local" / "nacos" / "apollo"）。 */
    String type();

    /** 是否启用动态热刷新（远端中心为 true，本地环境为 false）。 */
    boolean supportsDynamicRefresh();

    /** 读取单个配置项；不存在返回 empty。 */
    Optional<String> getProperty(String key);

    /** 读取单个配置项；不存在返回默认值。 */
    default String getProperty(String key, String defaultValue) {
        return getProperty(key).orElse(defaultValue);
    }

    /** 当前生效的配置项快照（key → value），通常仅含应用命名空间（如 app.*），避免泄露敏感配置。 */
    Map<String, String> snapshot();

    /**
     * 触发一次刷新（从中心重新拉取 / 本地环境重解析）。
     *
     * @return 刷新后生效的配置项数量
     */
    int refresh();
}
