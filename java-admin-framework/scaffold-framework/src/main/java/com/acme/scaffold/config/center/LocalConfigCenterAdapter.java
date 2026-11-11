package com.acme.scaffold.config.center;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/**
 * 本地配置中心适配器（R16 默认实现，零依赖）。
 *
 * <p>将 Spring {@link ConfigurableEnvironment}（application.yml / 环境变量 / 启动参数）视作「配置中心」，
 * 提供只读快照与刷新语义。本地环境下 {@code supportsDynamicRefresh()=false}：
 * {@link #refresh()} 仅重新遍历一次 {@link PropertySource} 重建快照，不会从远端拉取，
 * 返回值反映「当前 Environment 中已存在」的配置项数量。</p>
 *
 * <p><b>安全口径</b>：快照仅暴露应用命名空间（{@code app.*}）下的配置项，
 * 避免把系统属性 / 环境变量中的敏感信息（如密钥、数据库口令）混入回显。</p>
 */
public class LocalConfigCenterAdapter implements ConfigCenterPort {

    /** 仅暴露该前缀下的配置项，避免泄露敏感系统配置。 */
    private static final String KEY_PREFIX = "app.";

    private final ConfigurableEnvironment environment;

    public LocalConfigCenterAdapter(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public String type() {
        return "local";
    }

    @Override
    public boolean supportsDynamicRefresh() {
        return false;
    }

    @Override
    public java.util.Optional<String> getProperty(String key) {
        return java.util.Optional.ofNullable(environment.getProperty(key));
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        // 仅枚举 Map 型 PropertySource（yml/properties 的来源），跳过系统属性 / 环境变量等非 Map 源，
        // 既避免遍历海量系统键，也天然规避敏感配置泄露。
        for (PropertySource<?> ps : environment.getPropertySources()) {
            Object src = ps.getSource();
            if (src instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) src).entrySet()) {
                    Object v = e.getValue();
                    if (v == null) {
                        continue;
                    }
                    String key = String.valueOf(e.getKey());
                    if (key.startsWith(KEY_PREFIX) && !result.containsKey(key)) {
                        result.put(key, String.valueOf(v));
                    }
                }
            }
        }
        return Map.copyOf(result);
    }

    @Override
    public int refresh() {
        // 本地无远端可拉取；重建快照缓存即视为一次刷新，返回当前生效配置项数量。
        return snapshot().size();
    }
}
