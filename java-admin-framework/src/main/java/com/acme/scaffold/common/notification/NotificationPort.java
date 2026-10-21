package com.acme.scaffold.common.notification;

/**
 * 通知渠道适配器统一契约。每个具体渠道（站内信 / Webhook）实现该接口，
 * 由 {@link NotificationRouter} 按 {@link Channel} 路由到对应实现。
 *
 * <p>新增渠道只需新增一个 {@code NotificationPort} 实现并声明 {@link #supports}，
 * 编排层 {@code NotificationService} 通过 Spring 自动收集所有实现，无需改动既有代码。
 */
public interface NotificationPort {

    /** 该适配器负责的渠道。 */
    boolean supports(Channel channel);

    /**
     * 投递一条消息。
     *
     * @return 实际投递的「单元数」：IN_APP 为接收人数，WEBHOOK 为 1（一次外发）
     */
    int dispatch(NotificationMessage message);
}
