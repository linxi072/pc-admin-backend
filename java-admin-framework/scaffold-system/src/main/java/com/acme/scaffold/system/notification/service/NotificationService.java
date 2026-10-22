package com.acme.scaffold.system.notification.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.common.notification.NotificationRouter;
import com.acme.scaffold.system.entity.SysNotificationTemplateDO;
import com.acme.scaffold.system.notification.dto.SendWithTemplateNotificationRequest;
import com.acme.scaffold.system.rules.NotificationTemplateRules;
import com.acme.scaffold.system.service.NotificationTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 通知编排：按消息渠道路由到对应 {@link NotificationPort} 适配器并投递。
 *
 * <p>适配器列表由 Spring 自动收集所有 {@link NotificationPort} 实现，
 * 新增渠道只需新增一个实现类，无需改动本类。</p>
 *
 * <p>M11 起支持<b>按模板发送</b>（{@link #sendWithTemplate}）：模板为标题/正文/渠道/消息类型的
 * 唯一事实来源，发送侧取启用中模板、渲染 {@code ${var}} 后复用 {@link #dispatch} 链路，
 * 与原始直发（调用方自带 title/content）并存、向后兼容。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final List<NotificationPort> ports;
    private final NotificationTemplateService templateService;

    /** 投递一条消息，返回实际投递单元数（见各适配器语义）。 */
    public int dispatch(NotificationMessage message) {
        NotificationPort port = NotificationRouter.resolve(message.channel(), ports);
        return port.dispatch(message);
    }

    /**
     * 按通知模板发送（M11）：取启用中模板 → 渲染 {@code ${var}} → 按模板渠道投递。
     *
     * <p>渠道/标题/正文/消息类型全部取自模板（模板是唯一事实来源），请求侧只提供接收人与变量取值；
     * 模板缺失抛 {@code NOT_FOUND}、停用抛 {@code CONFLICT}（由 {@code requireEnabledByCode} 判定），
     * 模板渠道非法抛 {@code VALIDATION_ERROR}。</p>
     *
     * @return 实际投递单元数（见各适配器语义）
     */
    public int sendWithTemplate(SendWithTemplateNotificationRequest request) {
        SysNotificationTemplateDO tpl = templateService.requireEnabledByCode(request.templateCode());
        Map<String, String> vars = request.variables() == null ? Map.of() : request.variables();
        String chName = NotificationTemplateRules.normalizeChannel(tpl.getChannel());
        if (chName == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "模板渠道非法: " + tpl.getChannel());
        }
        Channel channel = Channel.valueOf(chName);
        String title = tpl.getTitle() == null ? null : NotificationTemplateRules.render(tpl.getTitle(), vars);
        String content = NotificationTemplateRules.render(tpl.getContent(), vars);
        NotificationMessage msg = new NotificationMessage(
                channel, title, content, tpl.getMsgType(),
                request.receiverIds(), request.roleIds(), request.orgIds(),
                request.webhookUrl(), null, request.emails(), request.mobiles());
        return dispatch(msg);
    }
}
