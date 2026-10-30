package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.realtime.port.RealtimeTemplatePort;
import com.acme.scaffold.realtime.port.RealtimeTemplateRenderResult;
import com.acme.scaffold.system.entity.SysNotificationTemplateDO;
import com.acme.scaffold.system.rules.NotificationTemplateRules;
import com.acme.scaffold.system.service.NotificationTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 实时推送模板渲染适配器：实现 realtime 模块 {@link RealtimeTemplatePort} 端口，
 * 复用 M10 通知模板中心（{@link NotificationTemplateService}）渲染实时推送负载。
 *
 * <p><b>仅接受「启用中」的 WEBSOCKET 渠道模板</b>：实时推送经 WebSocket 下行，非 WEBSOCKET 渠道
 * 模板（如 EMAIL/SMS）语义不符，显式拒绝。模板缺失 / 停用由
 * {@link NotificationTemplateService#requireEnabledByCode} 抛 {@code NOT_FOUND}/{@code CONFLICT}；
 * 渲染复用 {@link NotificationTemplateRules#render}，与 M11 send-with-template 口径同源。</p>
 *
 * <p>与 {@code JwtHandshakeAuthenticator} 同源模式：realtime 仅定义端口，本适配器在 system 模块注入，
 * 保持 realtime → system 单向依赖，不破坏模块边界。</p>
 */
@Component
@RequiredArgsConstructor
public class RealtimeTemplateAdapter implements RealtimeTemplatePort {

    private final NotificationTemplateService templateService;

    @Override
    public RealtimeTemplateRenderResult render(String templateCode, Map<String, String> variables) {
        SysNotificationTemplateDO tpl = templateService.requireEnabledByCode(templateCode);
        if (!Channel.WEBSOCKET.name().equals(tpl.getChannel())) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "实时推送仅支持 WEBSOCKET 渠道模板，当前模板渠道: " + tpl.getChannel());
        }
        Map<String, String> vars = variables == null ? Map.of() : variables;
        String title = NotificationTemplateRules.render(tpl.getTitle(), vars);
        String content = NotificationTemplateRules.render(tpl.getContent(), vars);
        return new RealtimeTemplateRenderResult(title, content, tpl.getMsgType());
    }
}
