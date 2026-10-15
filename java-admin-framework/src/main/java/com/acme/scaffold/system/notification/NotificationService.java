package com.acme.scaffold.system.notification;

import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.common.notification.NotificationRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知编排：按消息渠道路由到对应 {@link NotificationPort} 适配器并投递。
 *
 * <p>适配器列表由 Spring 自动收集所有 {@link NotificationPort} 实现，
 * 新增渠道只需新增一个实现类，无需改动本类。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final List<NotificationPort> ports;

    /** 投递一条消息，返回实际投递单元数（见各适配器语义）。 */
    public int dispatch(NotificationMessage message) {
        NotificationPort port = NotificationRouter.resolve(message.channel(), ports);
        return port.dispatch(message);
    }
}
