package com.acme.scaffold.system.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知投递故障转移配置（M16）：{@code app.notifications.failover.*}。
 *
 * <ul>
 *   <li>{@code enabled}：主适配器重试后仍失败时，是否继续尝试同渠道的<b>下一个</b>
 *       {@code NotificationPort} 实现（默认 false——见下方风险）。</li>
 * </ul>
 *
 * <p><b>适用场景</b>：同渠道注册了多个适配器实现（如主/备两条短信网关、双 SMTP 通道）。
 * 候选顺序即 Spring 收集 {@code List<NotificationPort>} 的顺序，首个实现为主、
 * 其余为备；主实现彻底失败（重试耗尽或确定性拒绝）后按序切换，首个成功即返回。</p>
 *
 * <p><b>为什么默认关闭</b>：故障转移是「至少一次」语义——若主适配器实际已投递但
 * 响应丢失（超时后成功），切备适配器会导致<b>重复投递</b>。邮件/短信/webhook 的
 * 接收方大多能容忍偶发重复，但这是有感知的副作用，故要求显式开启。仅重试
 * （默认开）的重复面显著更小：重试间隔短且不切换实现，且只发生在异常路径上。</p>
 *
 * <p>两者叠加时最坏延迟为「主实现重试 + 备实现重试」，调用方超时须覆盖该量级。</p>
 */
@ConfigurationProperties(prefix = "app.notifications.failover")
public class NotificationFailoverProperties {

    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
