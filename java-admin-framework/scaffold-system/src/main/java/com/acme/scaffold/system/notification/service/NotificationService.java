package com.acme.scaffold.system.notification.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.common.notification.NotificationRouter;
import com.acme.scaffold.system.entity.SysNotificationTemplateDO;
import com.acme.scaffold.system.notification.dto.SendWithTemplateNotificationRequest;
import com.acme.scaffold.system.notifylog.NotificationRecordRecorder;
import com.acme.scaffold.system.notifypref.NotificationPreferenceService;
import com.acme.scaffold.system.notifypref.rules.NotificationPreferenceRules;
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
 *
 * <p>M12 起每次 {@link #dispatch} 尝试（含被拒与失败）均落一条通知发送记录
 * （{@link NotificationRecordRecorder}，独立事务、脱敏摘要）——dispatch 是全部发送路径的
 * 唯一漏斗（原始 /send 直调、sendWithTemplate 内部复用），包一层即全覆盖五渠道审计。</p>
 *
 * <p>M13 起 dispatch 在路由前按<b>接收者偏好</b>过滤（{@link NotificationPreferenceService}）：
 * 用户退订的 (渠道 × 消息类型) 不再投递；定向接收者全部退订时记一条 {@code SKIPPED}
 * 并静默返回 0（不抛异常、不调适配器）。广播（角色/机构维度）与 WEBHOOK 无个人偏好语义，
 * 不过滤。自此 {@code sys_user} 的 {@code notify_*} 渠道总开关不再是「死开关」——
 * 发送链路首次读取它们。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final List<NotificationPort> ports;
    private final NotificationTemplateService templateService;
    private final NotificationRecordRecorder recordRecorder;
    private final NotificationPreferenceService preferenceService;

    /** 投递一条消息（无模板来源），返回实际投递单元数（见各适配器语义）。 */
    public int dispatch(NotificationMessage message) {
        return dispatch(message, null);
    }

    /**
     * 投递一条消息并留痕（M12），按接收者偏好过滤（M13）。
     *
     * <p>成功记 {@code SUCCESS} + 实际投递数；{@link RuntimeException}（渠道未启用、报文被拒、
     * 适配器/网关异常等）记 {@code FAILED} + 异常消息后<b>原样重抛</b>——记录是审计旁路，
     * 不改变投递结论，也不掩盖失败。</p>
     *
     * <p>M13 新增第三种终态：定向接收者全部退订 → 记 {@code SKIPPED} + 跳过原因，
     * <b>静默返回 0</b>（不抛异常：退订是正常业务结果而非故障；也不调适配器：
     * 没有该投递的对象）。过滤以原始消息为审计对象——记录里的「目标接收数」反映
     * 发送意图，退订差异由 SKIPPED 状态本身表达。</p>
     *
     * @param message      被投递的渠道无关消息
     * @param templateCode 来源模板编码（原始直发传 null，按模板发送回填以支撑 provenance 审计）
     * @return 实际投递单元数（见各适配器语义；全部退订为 0）
     */
    public int dispatch(NotificationMessage message, String templateCode) {
        try {
            NotificationMessage effective = preferenceService.applyPreferences(message);
            if (effective == null) {
                recordRecorder.skip(message, templateCode,
                        NotificationPreferenceRules.REASON_ALL_UNSUBSCRIBED);
                return 0;
            }
            int delivered = dispatchInternal(effective);
            recordRecorder.record(message, templateCode, delivered, true, null);
            return delivered;
        } catch (RuntimeException ex) {
            recordRecorder.record(message, templateCode, 0, false, ex.getMessage());
            throw ex;
        }
    }

    /** 实际路由与投递（不含记录旁路）。 */
    private int dispatchInternal(NotificationMessage message) {
        NotificationPort port = NotificationRouter.resolve(message.channel(), ports);
        return port.dispatch(message);
    }

    /**
     * 按通知模板发送（M11）：取启用中模板 → 渲染 {@code ${var}} → 按模板渠道投递。
     *
     * <p>渠道/标题/正文/消息类型全部取自模板（模板是唯一事实来源），请求侧只提供接收人与变量取值；
     * 模板缺失抛 {@code NOT_FOUND}、停用抛 {@code CONFLICT}（由 {@code requireEnabledByCode} 判定），
     * 模板渠道非法抛 {@code VALIDATION_ERROR}。模板编码随投递回填到通知发送记录（M12 provenance）。</p>
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
        return dispatch(msg, request.templateCode());
    }
}
