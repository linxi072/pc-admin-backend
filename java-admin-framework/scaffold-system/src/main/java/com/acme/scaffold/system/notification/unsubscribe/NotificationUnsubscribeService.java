package com.acme.scaffold.system.notification.unsubscribe;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.system.notification.unsubscribe.rules.UnsubscribeTokenRules;
import com.acme.scaffold.system.notification.unsubscribe.rules.UnsubscribeTokenRules.UnsubscribeTarget;
import com.acme.scaffold.system.notifypref.NotificationPreferenceService;
import com.acme.scaffold.system.notifypref.rules.NotificationPreferenceRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通知退订应用服务（M15）：邮件/短信正文中的「免登录退订」闭环。
 *
 * <p><b>合规动因</b>：M6 起的邮件/短信是事务性外发通道，监管（《通信短信息服务管理规定》、
 * CAN-SPAM 等）要求营销类外发必须提供可用的退订途径；本站内信/推送渠道已有 M13 偏好中心，
 * 邮件/短信作为「无会话外部通道」此前没有任何退订入口，本模块补齐。</p>
 *
 * <p><b>两条路径</b>：
 * <ul>
 *   <li><b>注入</b>（{@link #resolveLinks}）：发送侧按收件地址反查用户，逐用户铸造
 *       HMAC 签名退订链接，由邮件/短信适配器拼入正文；无法映射到用户的外部地址不注入
 *       （无 userId 即无退订对象）；</li>
 *   <li><b>消费</b>（{@link #unsubscribe(String)}）：公开端点验签 + 时效 + 写偏好，
 *       复用 {@link NotificationPreferenceService#unsubscribe}——落库即
 *       {@code sys_notification_preference} 一行 {@code enabled=0}，与个人中心
 *       「通知订阅」页、M13 发送前过滤完全同库同口径，退订立即对后续发送生效。</li>
 * </ul>
 *
 * <p><b>无状态令牌</b>：不建表、不落库——签名 + 过期时间即全部校验（设计见
 * {@link UnsubscribeTokenRules}）；重复点击幂等（第二次返回「已退订」而非报错）。
 * 令牌仅携带 (用户, 渠道, 类型) 能力，不含任何 PII 明文，日志泄露不直接暴露用户信息。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationUnsubscribeService {

    /** 公开退订端点路径（与控制器、安全白名单三处一致，单点定义）。 */
    public static final String UNSUBSCRIBE_PATH = "/api/notification/unsubscribe";

    private final NotificationUnsubscribeProperties properties;
    private final NotificationPreferenceService preferenceService;

    /** 功能是否实际生效（开关 + 密钥 + 基址齐备）。 */
    public boolean isActive() {
        return properties.isActive();
    }

    /**
     * 为单个用户铸造退订链接。
     *
     * @param userId  目标用户
     * @param channel 目标渠道（邮件/短信适配器传入，须支持偏好）
     * @param msgType 消息类型（空白/白名单外返回 {@code null}——无法归一即无法退订）
     * @return 完整退订 URL；功能未激活或目标不可归一时返回 {@code null}
     */
    public String unsubscribeUrl(Long userId, Channel channel, String msgType) {
        if (!isActive() || userId == null) {
            return null;
        }
        String type = NotificationPreferenceRules.normalizeMsgType(msgType);
        if (type == null || !NotificationPreferenceRules.supportsPreference(channel)) {
            return null;
        }
        long exp = System.currentTimeMillis()
                + Duration.ofHours(properties.getTtlHours()).toMillis();
        String token = UnsubscribeTokenRules.mint(properties.getSecret(), userId, channel, type, exp);
        return baseUrl() + UNSUBSCRIBE_PATH + "?token=" + token;
    }

    /**
     * 批量解析收件地址 → 退订链接（邮件/短信适配器正文注入用）。
     *
     * <p>键为归一化地址（email 小写、mobile trim 后原样），与
     * {@link NotificationPreferenceService#fetchAddressUserIds} 同口径；无法映射到
     * 用户的地址（外部收件人）不出现在结果中，注入方按「该收件人无链接」处理。</p>
     *
     * @param addresses 收件地址列表（已由渠道规则归一）
     * @param byEmail   true 按 email 列反查，false 按 mobile 列反查
     * @param channel   目标渠道（决定令牌承载的退订对象）
     * @param msgType   消息类型
     * @return 归一化地址 → 退订 URL；功能未激活或全军覆没时返回空 map
     */
    public Map<String, String> resolveLinks(List<String> addresses, boolean byEmail,
                                            Channel channel, String msgType) {
        if (!isActive() || addresses == null || addresses.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> addressUserIds = preferenceService.fetchAddressUserIds(addresses, byEmail);
        if (addressUserIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new HashMap<>(addressUserIds.size() * 2);
        for (Map.Entry<String, Long> entry : addressUserIds.entrySet()) {
            String url = unsubscribeUrl(entry.getValue(), channel, msgType);
            if (url != null) {
                out.put(entry.getKey(), url);
            }
        }
        return out;
    }

    /**
     * 执行退订（公开端点调用）：验签 + 时效 + 写偏好。
     *
     * <p>失败口径：令牌无效/篡改/过期一律抛 {@code VALIDATION_ERROR}（400），文案统一为
     * 「无效或已过期」——不区分失败原因，避免向探测方泄露校验细节。</p>
     *
     * @param token 退订令牌（来自正文链接查询串）
     * @return 退订结果（渠道、类型、是否本次发生变更、面向用户的文案）
     */
    public UnsubscribeOutcome unsubscribe(String token) {
        Optional<UnsubscribeTarget> target = UnsubscribeTokenRules.verify(
                properties.getSecret(), token, System.currentTimeMillis());
        if (target.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "退订链接无效或已过期，请从最新通知邮件中重新获取");
        }
        UnsubscribeTarget t = target.get();
        boolean changed = preferenceService.unsubscribe(t.userId(), t.channel(), t.msgType());
        String message = changed
                ? "已为您退订该类型通知，恢复订阅可在个人中心「通知订阅」中操作"
                : "您已退订该类型通知，无需重复操作";
        log.info("退订令牌消费成功 userId={} channel={} msgType={} changed={}",
                t.userId(), t.channel(), t.msgType(), changed);
        return new UnsubscribeOutcome(t.channel().name(), t.msgType(), changed, message);
    }

    /** 基址归一：去尾部斜杠，避免拼出双斜杠路径。 */
    private String baseUrl() {
        String base = properties.getBaseUrl().trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * 退订结果（公开端点响应体）：变更标志供前端区分「首次退订」与「重复点击」，
     * 文案字段让浏览器直接点击也能得到可读反馈。
     */
    public record UnsubscribeOutcome(String channel, String msgType, boolean changed,
                                     String message) {
    }
}
