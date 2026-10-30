package com.acme.scaffold.realtime.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.realtime.dto.RealtimeBroadcastRequest;
import com.acme.scaffold.realtime.dto.RealtimePushResultView;
import com.acme.scaffold.realtime.dto.RealtimeSessionView;
import com.acme.scaffold.realtime.dto.RealtimeUserPushRequest;
import com.acme.scaffold.realtime.gateway.GatewayNode;
import com.acme.scaffold.realtime.payload.RealtimeNotificationPayload;
import com.acme.scaffold.realtime.port.RealtimeTemplatePort;
import com.acme.scaffold.realtime.port.RealtimeTemplateRenderResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 实时推送管理应用服务：把 {@link GatewayNode}（在线会话只读视图与下行投递）与
 * {@link RealtimePushService}（定向 / 广播推送）编排为运维可调用能力。
 *
 * <p>原实现依赖 {@code RealtimeSessionManager}；本次整合后在线视图来自
 * {@link GatewayNode#onlineUserCounts()}/{@link GatewayNode#connectionCount()}，推送经由
 * {@link RealtimePushService}（内部走 GatewayNode）。仅读取内存会话态与触发推送，不落库。
 *
 * <p><b>M30 模板化</b>：请求可携带 {@code templateCode}+{@code variables} 复用通知模板中心
 * （经 {@link RealtimeTemplatePort} 防腐层，零耦合）渲染实时通知负载；端口缺失时若仍指定模板
 * 则显式失败，不静默降级。</p>
 *
 * <p>{@code GatewayNode} 未装配（{@code realtime.gateway.enabled=false}）时在线视图返回空 / 0。</p>
 */
@Service
@RequiredArgsConstructor
public class RealtimeAdminService {

    private final ObjectProvider<GatewayNode> gatewayNodeProvider;
    private final RealtimePushService pushService;
    private final ObjectProvider<RealtimeTemplatePort> templatePortProvider;

    /** 在线会话快照：userId → 活跃会话数，按 userId 升序。 */
    public List<RealtimeSessionView> listSessions() {
        GatewayNode node = gatewayNodeProvider.getIfAvailable();
        if (node == null) {
            return List.of();
        }
        Map<Long, Integer> counts = node.onlineUserCounts();
        return counts.entrySet().stream()
                .map(e -> new RealtimeSessionView(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(RealtimeSessionView::userId))
                .collect(Collectors.toList());
    }

    /** 当前在线会话总数（同一用户多端累加）。 */
    public int onlineCount() {
        GatewayNode node = gatewayNodeProvider.getIfAvailable();
        return node == null ? 0 : node.connectionCount();
    }

    /** 向所有在线会话广播，返回成功触达的会话数。 */
    public RealtimePushResultView broadcast(RealtimeBroadcastRequest request) {
        ResolvedPush resolved = resolve(request.type(), request.payload(),
                request.templateCode(), request.variables());
        int delivered = pushService.broadcast(resolved.type(), resolved.payload());
        return new RealtimePushResultView(delivered);
    }

    /** 向指定用户集合定向推送，返回成功触达的会话数。 */
    public RealtimePushResultView pushToUsers(RealtimeUserPushRequest request) {
        ResolvedPush resolved = resolve(request.type(), request.payload(),
                request.templateCode(), request.variables());
        int delivered = pushService.sendToUsers(request.userIds(), resolved.type(), resolved.payload());
        return new RealtimePushResultView(delivered);
    }

    /**
     * 解析推送目标：指定 {@code templateCode} 时经端口渲染为 NOTIFICATION 类型负载；
     * 否则回落原始 {@code type}/{@code payload} 路径（向后兼容 M29 既有调用）。
     *
     * @throws BusinessException 指定模板但端口未装配（模板渲染不可用）时
     */
    private ResolvedPush resolve(String type, Object payload, String templateCode, Map<String, String> variables) {
        if (templateCode != null && !templateCode.isBlank()) {
            RealtimeTemplatePort port = templatePortProvider.getIfAvailable();
            if (port == null) {
                throw new BusinessException(CommonErrorCode.CONFLICT,
                        "实时推送模板渲染不可用：未装配模板端口（请确认通知模板模块已启用）");
            }
            RealtimeTemplateRenderResult r = port.render(templateCode, variables);
            RealtimeNotificationPayload rendered = RealtimeNotificationPayload.of(
                    r.title(), r.content(), r.msgType(), Map.of());
            return new ResolvedPush(RealtimePushService.TYPE_NOTIFICATION, rendered);
        }
        return new ResolvedPush(type, payload);
    }

    /** 解析后的推送目标（类型 + 业务负载）。 */
    private record ResolvedPush(String type, Object payload) {
    }
}
