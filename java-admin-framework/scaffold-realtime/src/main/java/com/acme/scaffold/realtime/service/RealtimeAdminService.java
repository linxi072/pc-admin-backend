package com.acme.scaffold.realtime.service;

import com.acme.scaffold.realtime.dto.RealtimeBroadcastRequest;
import com.acme.scaffold.realtime.dto.RealtimePushResultView;
import com.acme.scaffold.realtime.dto.RealtimeSessionView;
import com.acme.scaffold.realtime.dto.RealtimeUserPushRequest;
import com.acme.scaffold.realtime.session.RealtimeSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 实时推送管理应用服务：把 {@link RealtimeSessionManager}（在线会话只读视图）与
 * {@link RealtimePushService}（定向 / 广播推送）编排为运维可调用能力。
 * <p>仅读取内存会话态与触发推送，不落库；在线会话本身由 WebSocket 生命周期维护。
 */
@Service
@RequiredArgsConstructor
public class RealtimeAdminService {

    private final RealtimeSessionManager sessionManager;
    private final RealtimePushService pushService;

    /** 在线会话快照：userId → 活跃会话数，按 userId 升序。 */
    public List<RealtimeSessionView> listSessions() {
        Map<Long, Integer> counts = sessionManager.onlineUserCounts();
        return counts.entrySet().stream()
                .map(e -> new RealtimeSessionView(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(RealtimeSessionView::userId))
                .collect(Collectors.toList());
    }

    /** 当前在线会话总数（同一用户多端累加）。 */
    public int onlineCount() {
        return sessionManager.onlineCount();
    }

    /** 向所有在线会话广播，返回成功触达的会话数。 */
    public RealtimePushResultView broadcast(RealtimeBroadcastRequest request) {
        int delivered = pushService.broadcast(request.type(), request.payload());
        return new RealtimePushResultView(delivered);
    }

    /** 向指定用户集合定向推送，返回成功触达的会话数。 */
    public RealtimePushResultView pushToUsers(RealtimeUserPushRequest request) {
        int delivered = pushService.sendToUsers(request.userIds(), request.type(), request.payload());
        return new RealtimePushResultView(delivered);
    }
}
