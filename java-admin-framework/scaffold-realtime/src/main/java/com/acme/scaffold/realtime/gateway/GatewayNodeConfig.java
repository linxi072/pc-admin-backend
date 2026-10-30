package com.acme.scaffold.realtime.gateway;

import com.acme.scaffold.common.messaging.bus.MessageBus;
import com.acme.scaffold.realtime.gateway.bus.LocalBus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.net.InetAddress;
/**
 * 实时网关节点装配（{@code realtime.gateway}）。
 *
 * <p>职责：把传输无关的 {@link GatewayNode} 编排器装配为单例 Bean，喂入
 * {@link MessageBus}（Kafka 网关启用时为 {@code KafkaBus}，否则为本地 {@link LocalBus} 兜底）
 * 与 {@link Clock}（生产用系统时钟）；并驱动心跳 tick 与优雅退出。</p>
 *
 * <p>{@code realtime.gateway.enabled=false} 时整体不装配，{@code RealtimeWebSocketHandler}
 * 以 {@code ObjectProvider#getIfAvailable()} 探测，未装配则降级为「仅握手拒绝」。</p>
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "realtime.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GatewayNodeConfig {

    private final Clock clock = Clock.System.instance();
    private final ObjectProvider<GatewayNode> gatewayNodes;

    public GatewayNodeConfig(ObjectProvider<GatewayNode> gatewayNodes) {
        this.gatewayNodes = gatewayNodes;
    }

    /** 无 Kafka 集群环境下的兜底总线（Kafka 网关启用时被 {@code KafkaBus} 取代）。 */
    @Bean
    @ConditionalOnMissingBean(MessageBus.class)
    public MessageBus localMessageBus() {
        return new LocalBus();
    }

    @Bean
    public GatewayNode gatewayNode(GatewayProperties props, MessageBus bus) {
        String gatewayId = resolveGatewayId(props);
        return new GatewayNode(props.toGatewaySettings(gatewayId), bus, clock, gatewayId);
    }

    /**
     * 心跳 tick：周期触发 {@link GatewayNode#onTick}，发 ping / 断空闲 / 清幽灵路由 / 节点熔断。
     *
     * <p>Spring 约束 @Scheduled 方法必须无参，故节点经 {@link ObjectProvider} 延迟获取
     * （与 {@code RealtimeWebSocketHandler} 同款注入方式），避免配置类与同类 @Bean
     * 形成构造期依赖；节点未装配时自然跳过。
     */
    @Scheduled(fixedDelayString = "${realtime.gateway.heartbeat.ping-interval-ms:30000}")
    public void heartbeatTick() {
        gatewayNodes.ifAvailable(node -> node.onTick(clock.nowMillis()));
    }

    /** 优雅退出：进程关闭时推进七步状态机（含全量 1001 关闭客户端），带时间推进兜底。 */
    @Bean
    public SmartLifecycle gatewayShutdownLifecycle(GatewayNode gatewayNode) {
        return new SmartLifecycle() {
            private volatile boolean running = false;

            @Override
            public void start() {
                running = true;
            }

            @Override
            public void stop() {
                long now = clock.nowMillis();
                // 以「模拟时间截止」驱动而非固定尝试次数：最坏路径为 3 步原子迁移
                // + in-flight 排空 30s 超时（每秒一步，共 30 步）+ flush 1~2 步 ≈ 35 步，
                // 固定 24 次上限不足以覆盖，会跳过 TERMINATED 的路由表清理即退出。
                long deadline = now + gatewayNode.settings().drainInflightTimeoutMs()
                        + gatewayNode.settings().drainFlushTimeoutMs() + 10_000L;
                DrainState.DrainStep step = gatewayNode.drainStep();
                while (!DrainState.DrainStep.TERMINATED.equals(step) && now < deadline) {
                    now += 1000; // 模拟时间推进以越过各步超时门槛
                    step = gatewayNode.drainTick(now);
                }
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MAX_VALUE; // 最后关闭，先于其他组件回收连接
            }
        };
    }

    private static String resolveGatewayId(GatewayProperties props) {
        if (props.getGatewayId() != null && !props.getGatewayId().isBlank()) {
            return props.getGatewayId();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "realtime-" + Long.toHexString(System.currentTimeMillis());
        }
    }
}
