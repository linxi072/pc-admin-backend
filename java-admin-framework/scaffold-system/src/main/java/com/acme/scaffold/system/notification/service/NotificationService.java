package com.acme.scaffold.system.notification.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.common.notification.NotificationRouter;
import com.acme.scaffold.system.entity.SysNotificationTemplateDO;
import com.acme.scaffold.system.notification.config.NotificationFailoverProperties;
import com.acme.scaffold.system.notification.config.NotificationRetryProperties;
import com.acme.scaffold.system.notification.dto.SendWithTemplateNotificationRequest;
import com.acme.scaffold.system.notification.rules.DeliveryRetryRules;
import com.acme.scaffold.system.notifydigest.NotificationDigestService;
import com.acme.scaffold.system.notifydigest.NotificationDigestService.EnqueueResult;
import com.acme.scaffold.system.notifydigest.domain.SysNotificationDigestDO;
import com.acme.scaffold.system.notifydigest.rules.NotificationDigestRules;
import com.acme.scaffold.system.notifylog.NotificationRecordRecorder;
import com.acme.scaffold.system.notifypref.NotificationPreferenceService;
import com.acme.scaffold.system.notifypref.rules.NotificationPreferenceRules;
import com.acme.scaffold.system.notifyquiet.NotificationQuietHoursService;
import com.acme.scaffold.system.notifyquiet.rules.NotificationQuietHoursRules;
import com.acme.scaffold.system.rules.NotificationTemplateRules;
import com.acme.scaffold.system.service.NotificationTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
 *
 * <p>M14 起 dispatch 在偏好过滤<b>之前</b>先按<b>免打扰时段</b>过滤
 * （{@link NotificationQuietHoursService}，时间维总闸）：处于免打扰窗口内的定向接收者
 * 不投递（{@code ALERT} 可按用户配置的告警穿透例外）；全部定向接收者均被抑制时记一条
 * {@code SKIPPED}（原因 {@value NotificationQuietHoursRules#REASON_QUIET_HOURS}）并静默返回 0。
 * 两道过滤均只作用于定向接收者；广播与 WEBHOOK 无个人作息/偏好语义，不过滤。</p>
 *
 * <p>M16 起 dispatch 在适配器投递层增加<b>有界重试</b>与<b>故障转移</b>
 * （{@link NotificationRetryProperties} / {@link NotificationFailoverProperties}）：
 * 瞬时故障（非 {@code BusinessException} 的运行时异常）按次数 + 线性退避重试；
 * 显式开启故障转移后，主适配器彻底失败时按收集顺序切换同渠道备用适配器。
 * 两者均只包裹「路由之后的实际投递」，不改变 M12 记录契约——每次
 * {@link #dispatch} 仍只落一条终态记录（成功 / 失败 / 跳过）。</p>
 *
 * <p>M17 起 dispatch 在两道接收侧过滤<b>之后</b>、实际路由<b>之前</b>经
 * <b>聚合摘要</b>挂点（{@link NotificationDigestService}）：命中
 * {@code app.notifications.digest.msg-types} 的定向消息不再立即投递，而是合并进
 * {@code sys_notification_digest} 聚合行，原消息记 {@code DIGEST_PENDING} 静默返回 0；
 * 合并数达阈值的聚合行在同一 dispatch 内立即刷新为摘要消息投递（摘要消息
 * msgType 恒为 {@code DIGEST}，不在聚合配置内故不会递归挂起，也不在 M13 偏好
 * 白名单内故默认开通、仅受渠道总开关约束——天然复用 M13/M14/M16 全链路）。
 * 低流量用户的滞留行由 {@code NotificationDigestJob} 按 max-wait-minutes 兜底刷新。
 * 聚合只作用于「本来会投递」的消息（挂点位于 M14/M13 之后），广播与 WEBHOOK 不聚合。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final List<NotificationPort> ports;
    private final NotificationTemplateService templateService;
    private final NotificationRecordRecorder recordRecorder;
    private final NotificationPreferenceService preferenceService;
    private final NotificationQuietHoursService quietHoursService;
    private final NotificationRetryProperties retryProperties;
    private final NotificationFailoverProperties failoverProperties;
    private final NotificationDigestService digestService;

    /** 投递一条消息（无模板来源），返回实际投递单元数（见各适配器语义）。 */
    public int dispatch(NotificationMessage message) {
        return dispatch(message, null);
    }

    /**
     * 投递一条消息并留痕（M12），按免打扰时段（M14）与接收者偏好（M13）过滤。
     *
     * <p>成功记 {@code SUCCESS} + 实际投递数；{@link RuntimeException}（渠道未启用、报文被拒、
     * 适配器/网关异常等）记 {@code FAILED} + 异常消息后<b>原样重抛</b>——记录是审计旁路，
     * 不改变投递结论，也不掩盖失败。</p>
     *
     * <p>两道接收侧过滤（均只作用于定向接收者，广播/WEBHOOK 不过滤）：
     * <ol>
     *   <li>免打扰时段（M14，时间维总闸）：全部定向接收者均处于免打扰 → 记 {@code SKIPPED}
     *       （原因 {@value NotificationQuietHoursRules#REASON_QUIET_HOURS}），静默返回 0；</li>
     *   <li>接收者偏好（M13，渠道 × 类型维）：剩余接收者全部退订 → 记 {@code SKIPPED}
     *       （原因 {@value NotificationPreferenceRules#REASON_ALL_UNSUBSCRIBED}），静默返回 0。</li>
     * </ol>
     * 静默语义：退订与作息抑制都是正常业务结果而非故障，不抛异常；也没有该投递的对象，
     * 不调适配器。过滤以原始消息为审计对象——记录里的「目标接收数」反映发送意图，
     * 抑制差异由 SKIPPED 状态与原因本身表达。</p>
     *
     * <p>M17 聚合摘要（第三道挂点，位于两道过滤之后）：命中聚合配置的定向消息记
     * {@code DIGEST_PENDING}（原因 {@value NotificationDigestRules#REASON_DIGEST_PENDING}）
     * 并静默返回 0；合并达阈值的聚合行在本方法内立即刷新投递（见 {@link #flushDigestRows}）。
     * 聚合失败（写库异常）按 FAILED 记录并上抛——消息未投递，静默吞掉即真丢失。</p>
     *
     * @param message      被投递的渠道无关消息
     * @param templateCode 来源模板编码（原始直发传 null，按模板发送回填以支撑 provenance 审计）
     * @return 实际投递单元数（见各适配器语义；全部被抑制或被聚合吞并为 0）
     */
    public int dispatch(NotificationMessage message, String templateCode) {
        try {
            NotificationMessage effective = quietHoursService.applyQuietHours(message);
            if (effective == null) {
                recordRecorder.skip(message, templateCode,
                        NotificationQuietHoursRules.REASON_QUIET_HOURS);
                return 0;
            }
            effective = preferenceService.applyPreferences(effective);
            if (effective == null) {
                recordRecorder.skip(message, templateCode,
                        NotificationPreferenceRules.REASON_ALL_UNSUBSCRIBED);
                return 0;
            }
            EnqueueResult digest = digestService.enqueue(effective);
            if (digest.digested()) {
                // 消息已进聚合队列：原消息不投递，记 DIGEST_PENDING（聚合中是独立生命周期态，
                // 不计失败率）；已达阈值的聚合行立即刷新，先到先投防囤积
                recordRecorder.pending(message, templateCode,
                        NotificationDigestRules.REASON_DIGEST_PENDING);
                flushDigestRows(digest.flushRows());
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

    /**
     * 刷新已达阈值的聚合行：构造摘要消息并递归走完整 {@link #dispatch} 投递
     * （摘要消息复用 M12 留痕 / M13 偏好 / M14 免打扰 / M16 重试与故障转移全链路），
     * 随后按一次性语义删行。
     *
     * <p>摘要投递异常（含 {@code buildDigestMessage} 返回 null 的无处可投）仅 warn 日志，
     * 不影响原消息结论——原消息已记 {@code DIGEST_PENDING} 并返回 0；失败由摘要自身的
     * M12 记录呈现。行级 {@code finally} 删行保证聚合缓冲不囤积：聚合行是待投递缓冲
     * 而非档案，反复重投只会制造 FAILED 日志轰炸，审计由 M12 承担。</p>
     */
    private void flushDigestRows(List<SysNotificationDigestDO> flushRows) {
        for (SysNotificationDigestDO row : flushRows) {
            try {
                NotificationMessage digestMessage = digestService.buildDigestMessage(row);
                if (digestMessage != null) {
                    dispatch(digestMessage, null);
                }
            } catch (RuntimeException ex) {
                log.warn("摘要刷新投递失败 digestId={} userId={} err={}",
                        row.getId(), row.getUserId(), ex.toString());
            } finally {
                digestService.delete(row.getId());
            }
        }
    }

    /**
     * 实际路由与投递（M16：含适配器级有界重试与可选的同渠道故障转移）。
     *
     * <p><b>候选适配器</b>：故障转移关闭时仅 {@link NotificationRouter#resolve} 选出的
     * 首个实现（与 M15 及以前行为一致）；开启时取该渠道全部实现，按 Spring 收集顺序
     * （首个为主、其余为备）逐个尝试，首个成功即返回，全部失败抛最后一个异常。</p>
     *
     * <p><b>重试</b>：每个候选适配器独立重试——瞬时故障（非
     * {@code BusinessException}）按 {@code retry.max-attempts} 次内、每次前线性退避
     * {@code attempt × retry.backoff-ms} 重试；业务确定性拒绝立即放弃该候选。
     * 等待被中断时恢复中断标志并按失败处理，不伪装成功。</p>
     */
    private int dispatchInternal(NotificationMessage message) {
        List<NotificationPort> candidates = failoverProperties.isEnabled()
                ? supportingPorts(message.channel())
                : List.of(NotificationRouter.resolve(message.channel(), ports));
        RuntimeException lastFailure = null;
        for (NotificationPort port : candidates) {
            try {
                return dispatchWithRetry(port, message);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("渠道投递失败 channel={} port={} err={}",
                        message.channel(), port.getClass().getSimpleName(), ex.toString());
            }
        }
        // candidates 至少一个（无匹配时 supportingPorts/resolve 已抛异常），此处必然非空
        throw lastFailure;
    }

    /** 同渠道全部适配器（保序）；无匹配时抛与 {@link NotificationRouter} 同口径的校验异常。 */
    private List<NotificationPort> supportingPorts(Channel channel) {
        List<NotificationPort> matched = new ArrayList<>();
        for (NotificationPort port : ports) {
            if (port.supports(channel)) {
                matched.add(port);
            }
        }
        if (matched.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "不支持的通知渠道: " + channel);
        }
        return matched;
    }

    /** 单适配器有界重试：成功即返回；不可重试或次数耗尽则原样上抛（异常类型与消息不变）。 */
    private int dispatchWithRetry(NotificationPort port, NotificationMessage message) {
        int maxAttempts = retryProperties.isEnabled()
                ? DeliveryRetryRules.clampAttempts(retryProperties.getMaxAttempts())
                : 1;
        long backoffMs = Math.max(0, retryProperties.getBackoffMs());
        for (int attempt = 1; ; attempt++) {
            try {
                return port.dispatch(message);
            } catch (RuntimeException ex) {
                if (attempt >= maxAttempts || !DeliveryRetryRules.isRetryable(ex)) {
                    throw ex;
                }
                log.warn("投递失败将重试 channel={} port={} nextAttempt={}/{} err={}",
                        message.channel(), port.getClass().getSimpleName(),
                        attempt + 1, maxAttempts, ex.toString());
                if (!sleepQuietly(DeliveryRetryRules.backoffMs(attempt, backoffMs))) {
                    // 等待被中断：已恢复中断标志，停止重试并按失败处理（不切换、不伪装成功）
                    throw ex;
                }
            }
        }
    }

    /**
     * 静默等待退避时长。
     *
     * @return {@code true} 完整等待；{@code false} 等待被中断（已恢复中断标志）
     */
    private static boolean sleepQuietly(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
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
