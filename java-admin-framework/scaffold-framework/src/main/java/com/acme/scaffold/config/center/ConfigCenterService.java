package com.acme.scaffold.config.center;

import com.acme.scaffold.common.event.EventPublisher;
import com.acme.scaffold.common.event.domain.ConfigRefreshedEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 配置中心应用服务（R16）：封装端口调用并发布刷新事件。
 *
 * <p>读取走 {@link ConfigCenterPort}；{@link #refresh()} 在端口刷新后发布
 * {@link ConfigRefreshedEvent}（R2 事件总线），订阅方可据此热重载运行态。
 * 发布经 {@link EventPublisher} 错误隔离，订阅方异常不影响刷新返回值。</p>
 *
 * <p>快照默认仅暴露 {@link ConfigCenterProperties#getSnapshotPrefix()} 白名单前缀下的配置项，
 * 避免把敏感配置（密钥、口令）回显到管理接口。</p>
 */
@Slf4j
public class ConfigCenterService {

    private final ConfigCenterPort port;
    private final EventPublisher eventPublisher;
    private final ConfigCenterProperties properties;

    public ConfigCenterService(ConfigCenterPort port, EventPublisher eventPublisher,
                               ConfigCenterProperties properties) {
        this.port = port;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /** 配置中心类型（local / nacos / apollo）。 */
    public String type() {
        return port.type();
    }

    /** 是否支持动态热刷新。 */
    public boolean supportsDynamicRefresh() {
        return port.supportsDynamicRefresh();
    }

    /** 读取单个配置项。 */
    public Optional<String> getProperty(String key) {
        return port.getProperty(key);
    }

    /** 当前生效配置项数量（按白名单前缀过滤后）。 */
    public int snapshotSize() {
        return snapshot(null).size();
    }

    /**
     * 配置快照（按前缀过滤）。
     *
     * @param explicitPrefix 显式前缀（逗号分隔）；为空时回落到 {@link ConfigCenterProperties#getSnapshotPrefix()}
     */
    public Map<String, String> snapshot(String explicitPrefix) {
        Map<String, String> all = port.snapshot();
        List<String> prefixes = parsePrefixes(explicitPrefix != null && !explicitPrefix.isBlank()
                ? explicitPrefix : properties.getSnapshotPrefix());
        if (prefixes.isEmpty()) {
            return all;
        }
        Map<String, String> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : all.entrySet()) {
            for (String p : prefixes) {
                if (!p.isEmpty() && e.getKey().startsWith(p)) {
                    filtered.put(e.getKey(), e.getValue());
                    break;
                }
            }
        }
        return filtered;
    }

    /**
     * 触发热刷新：端口刷新后发布 {@link ConfigRefreshedEvent}。
     *
     * @return 刷新后生效的配置项数量
     */
    public int refresh() {
        int count = port.refresh();
        try {
            eventPublisher.publish(new ConfigRefreshedEvent(null, "config-center", port.type(),
                    port.supportsDynamicRefresh(), null, count));
        } catch (Exception e) {
            log.warn("配置刷新事件发布失败（不影响刷新结果）", e);
        }
        return count;
    }

    private static List<String> parsePrefixes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
