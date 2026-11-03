package com.acme.scaffold.security.online;

import com.acme.scaffold.common.event.DomainEventListener;
import com.acme.scaffold.common.event.domain.UserDisabledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户被禁用事件订阅者（ROADMAP R2 跨域副作用示范）。
 *
 * <p>由 {@code system} 模块发布 {@link UserDisabledEvent}，本订阅者将其落为「踢下线」动作，
 * 经防腐层端口 {@link OnlineSessionDataPort} 完成，<b>不反向依赖 system</b>。
 * 这样「禁用用户」这一写操作的解耦收益是：新增下游响应（通知 / 审计 / 清缓存）无需改动 system 代码。</p>
 *
 * <p>发布方已做错误隔离，本订阅者自身也应自愈：异常仅记录日志，不向外抛，避免拖累事件总线。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDisabledSessionRevoker implements DomainEventListener<UserDisabledEvent> {

    private final OnlineSessionDataPort onlineSessionPort;

    @Override
    public Class<UserDisabledEvent> eventType() {
        return UserDisabledEvent.class;
    }

    @Override
    public void onEvent(UserDisabledEvent event) {
        try {
            int affected = onlineSessionPort.markOfflineByUser(event.getUserId(),
                    OnlineSessionDataPort.REASON_DISABLED);
            log.info("用户被禁用，已触发踢下线 userId={} username={} 影响会话数={}",
                    event.getUserId(), event.getUsername(), affected);
        } catch (Exception ex) {
            log.error("处理用户禁用事件失败 userId={} eventId={}",
                    event.getUserId(), event.getEventId(), ex);
        }
    }
}
