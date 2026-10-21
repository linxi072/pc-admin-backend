package com.acme.scaffold.monitor.alert;

import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 告警接收事件：在告警入箱后发布，供其它组件响应（如转发至外部网关、站内信通知、WebSocket 推送）。
 */
public class AlertReceivedEvent extends ApplicationEvent {

    private final transient List<AlertInboxEntry> entries;

    public AlertReceivedEvent(Object source, List<AlertInboxEntry> entries) {
        super(source);
        this.entries = entries;
    }

    public List<AlertInboxEntry> getEntries() {
        return entries;
    }
}
