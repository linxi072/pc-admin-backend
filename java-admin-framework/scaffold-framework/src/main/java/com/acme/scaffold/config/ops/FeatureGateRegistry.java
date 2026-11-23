package com.acme.scaffold.config.ops;

import java.util.List;

/**
 * 运维开关单一事实来源（E2 统一运维控制台）：集中登记各 feature-gate 的配置前缀/名称，
 * 消除散落在各 {@code @ConditionalOnProperty} 注解中的魔法字符串。
 *
 * <p>本类只定义元数据（key + 中文描述 + 默认态），实际开关值由
 * {@code OpsConsoleController} 从 Spring {@code Environment} 读取；不引入对任何
 * 特性 Bean 的编译期依赖，故任一开关关闭时聚合接口仍可用（这是运维控制台的前提）。</p>
 *
 * <p>纯 Java、无 Spring 依赖，便于离线单测。</p>
 */
public final class FeatureGateRegistry {

    /** 一个开关的元数据。 */
    public record Gate(String property, String label, boolean defaultEnabled) {
        /** 从 Environment 读取时使用的完整属性名（prefix.name 形式）。 */
        public String fullKey() {
            return property;
        }
    }

    /**
     * 全部已登记的运维开关（顺序稳定，供前端展示）。
     * {@code defaultEnabled} 与各 {@code @ConditionalOnProperty(matchIfMissing)} 保持一致：
     * 未声明 matchIfMissing 的默认关闭（false），声明的默认开启（true）。
     */
    public static final List<Gate> ALL = List.of(
            new Gate("gateway.enabled", "网关前置层", false),
            new Gate("app.chaos.enabled", "混沌工程", false),
            new Gate("app.config-center.enabled", "配置中心", false),
            new Gate("app.multiregion.enabled", "多区域路由", false),
            new Gate("app.cache.enabled", "缓存统计", true),
            new Gate("app.quartz.enabled", "任务调度", true),
            new Gate("ai.llm.enabled", "AI 辅助（云模型）", false),
            new Gate("realtime.gateway.enabled", "实时通道网关", true),
            new Gate("realtime.gateway.hmac.enabled", "实时通道 HMAC", false),
            new Gate("kafka.gateway.enabled", "Kafka 网关", false),
            new Gate("kafka.ordered-consumer.enabled", "Kafka 保序消费", false)
    );

    private FeatureGateRegistry() {
    }

    /** 按属性名查找开关元数据；未登记返回 null。 */
    public static Gate find(String property) {
        if (property == null) {
            return null;
        }
        for (Gate g : ALL) {
            if (g.property().equals(property)) {
                return g;
            }
        }
        return null;
    }

    /** 是否存在该开关（供前端/测试校验清单一致性）。 */
    public static boolean contains(String property) {
        return find(property) != null;
    }

    /** 开关总数（供聚合接口与前端做 completeness 校验）。 */
    public static int size() {
        return ALL.size();
    }
}
