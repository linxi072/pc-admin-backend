package com.acme.scaffold.realtime.service;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.realtime.domain.RealtimeBroadcastRequest;
import com.acme.scaffold.realtime.domain.RealtimePushResultView;
import com.acme.scaffold.realtime.domain.RealtimeSessionView;
import com.acme.scaffold.realtime.domain.RealtimeUserPushRequest;
import com.acme.scaffold.realtime.gateway.GatewayNode;
import com.acme.scaffold.realtime.payload.RealtimeNotificationPayload;
import com.acme.scaffold.realtime.port.RealtimeTemplatePort;
import com.acme.scaffold.realtime.port.RealtimeTemplateRenderResult;
import com.acme.scaffold.realtime.port.RealtimeUserDirectoryPort;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

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
 *
 * <p><b>可见范围</b>：在线会话与推送目标统一经 {@link RealtimeUserDirectoryPort} 按当前主体租户收敛；
 * 无主体或目录端口缺失时 fail-closed，避免管理接口成为跨租户枚举 / 消息注入的旁路。</p>
 */
@Service
@RequiredArgsConstructor
public class RealtimeAdminService {

    private final ObjectProvider<GatewayNode> gatewayNodeProvider;
    private final RealtimePushService pushService;
    private final ObjectProvider<RealtimeTemplatePort> templatePortProvider;
    private final ObjectProvider<RealtimeUserDirectoryPort> userDirectoryProvider;
    private final SecurityContextFacade securityContextFacade;

    /** 在线会话快照：仅当前租户可见用户，userId → 活跃会话数，按 userId 升序。 */
    public List<RealtimeSessionView> listSessions() {
        GatewayNode node = gatewayNodeProvider.getIfAvailable();
        if (node == null) {
            return List.of();
        }
        Set<Long> visible = visibleUserIds();
        Map<Long, Integer> counts = node.onlineUserCounts();
        return counts.entrySet().stream()
                .filter(e -> visible.contains(e.getKey()))
                .map(e -> new RealtimeSessionView(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(RealtimeSessionView::userId))
                .collect(Collectors.toList());
    }

    /** 当前在线会话总数（仅可见用户，同一用户多端累加）。 */
    public int onlineCount() {
        return listSessions().stream().mapToInt(RealtimeSessionView::sessionCount).sum();
    }

    /** 向当前租户可见的在线用户广播，返回成功触达的用户数。 */
    public RealtimePushResultView broadcast(RealtimeBroadcastRequest request) {
        ResolvedPush resolved = resolve(request.type(), request.payload(),
                request.templateCode(), request.variables());
        Set<Long> visible = visibleUserIds();
        int delivered = visible.isEmpty()
                ? 0 : pushService.sendToUsers(visible, resolved.type(), resolved.payload());
        return new RealtimePushResultView(delivered);
    }

    /** 向指定用户集合定向推送；任一目标不在可见范围内即拒绝整个请求，不泄露越权用户是否存在。 */
    public RealtimePushResultView pushToUsers(RealtimeUserPushRequest request) {
        Set<Long> visible = visibleUserIds();
        List<Long> targets = request.userIds() == null
                ? List.of() : request.userIds().stream().filter(Objects::nonNull).toList();
        if (targets.stream().anyMatch(id -> !visible.contains(id))) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "目标用户不在当前数据范围内");
        }
        ResolvedPush resolved = resolve(request.type(), request.payload(),
                request.templateCode(), request.variables());
        int delivered = targets.isEmpty()
                ? 0 : pushService.sendToUsers(targets, resolved.type(), resolved.payload());
        return new RealtimePushResultView(delivered);
    }

    /** 当前主体的可见用户集合：无主体 / 无目录端口时 fail-closed，不返回任何跨租户数据。 */
    private Set<Long> visibleUserIds() {
        CurrentPrincipal principal = securityContextFacade.getCurrentPrincipal().orElse(null);
        if (principal == null) {
            return Set.of();
        }
        long tenantId = principal.tenantId() == null ? 0L : principal.tenantId();
        RealtimeUserDirectoryPort directory = userDirectoryProvider.getIfAvailable();
        return directory == null ? Set.of() : directory.userIdsInTenant(tenantId);
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
