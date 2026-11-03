package com.acme.scaffold.common.event.domain;

import com.acme.scaffold.common.event.DomainEvent;

/**
 * 配置热刷新完成事件（ROADMAP R16 · 配置中心接入）。
 *
 * <p>由 {@code ConfigCenterService} 在执行一次配置刷新（从配置中心重新拉取或本地环境重解析）后发布，
 * 订阅方据此重新加载受配置影响的运行态（如缓存 TTL、限流阈值、租户路由表），实现「无需重启热更新」。</p>
 *
 * <p>发布经 {@code EventPublisher} 错误隔离：单个订阅者失败不影响其他订阅者与刷新返回值。
 * 事件本身不要求事务边界，可在 {@code @Transactional} 之内或之外发布。</p>
 */
public class ConfigRefreshedEvent extends DomainEvent {

    /** 本次刷新的配置中心类型（local / nacos / apollo）。 */
    private final String centerType;

    /** 刷新是否真正从远端拉取（local 为 false，仅重解析本地环境）。 */
    private final boolean remote;

    /** 刷新涉及的数据 ID / 配置分组（可为 null，表示全量刷新）。 */
    private final String dataId;

    /** 刷新后生效的配置项数量（便于审计与排查）。 */
    private final int effectiveCount;

    public ConfigRefreshedEvent(Long tenantId, String source, String centerType,
                                boolean remote, String dataId, int effectiveCount) {
        super(tenantId, source);
        this.centerType = centerType;
        this.remote = remote;
        this.dataId = dataId;
        this.effectiveCount = effectiveCount;
    }

    public String getCenterType() {
        return centerType;
    }

    public boolean isRemote() {
        return remote;
    }

    public String getDataId() {
        return dataId;
    }

    public int getEffectiveCount() {
        return effectiveCount;
    }
}
