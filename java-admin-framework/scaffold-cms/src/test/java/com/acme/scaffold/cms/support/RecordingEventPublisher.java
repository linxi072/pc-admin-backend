package com.acme.scaffold.cms.support;

import com.acme.scaffold.common.event.DomainEvent;
import com.acme.scaffold.common.event.EventPublisher;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用事件发布器：记录所有被发布的事件，便于断言「文章发布」等副作用确实广播。
 */
public class RecordingEventPublisher implements EventPublisher {

    private final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
        published.add(event);
    }

    @Override
    public void publishAll(List<? extends DomainEvent> events) {
        published.addAll(events);
    }

    public List<DomainEvent> publishedEvents() {
        return published;
    }
}
