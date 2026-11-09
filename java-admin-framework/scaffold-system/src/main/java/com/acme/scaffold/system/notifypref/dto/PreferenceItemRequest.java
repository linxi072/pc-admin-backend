package com.acme.scaffold.system.notifypref.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 单格订阅偏好项：(渠道 × 消息类型) → 是否接收。
 *
 * <p>{@code enabled} 三态语义：
 * <ul>
 *   <li>{@code 1}：接收（upsert 落行）；</li>
 *   <li>{@code 0}：免打扰（upsert 落行，发送前过滤掉本人）；</li>
 *   <li>{@code null}：恢复默认（物理删行，回到「无行默认开通 + 渠道总开关兜底」）。</li>
 * </ul>
 * 「恢复默认」用删行而非存 {@code enabled=1} 表达，是为了让「用户从未设置过」与
 * 「用户设过又还原」在库里同构——均无行，前端也能据此标注默认来源。</p>
 *
 * @param channel 渠道（IN_APP / EMAIL / SMS / WEBSOCKET；WEBHOOK 无个人偏好语义，拒绝）
 * @param msgType 消息类型（NOTICE / ALERT / SYSTEM / CC；空归一为 NOTICE）
 * @param enabled 开关：0/1 设置，null 恢复默认
 */
public record PreferenceItemRequest(
        @NotBlank(message = "{validation.channel.notBlank}")
        String channel,
        String msgType,
        @Min(value = 0, message = "{validation.preference.switch.range}")
        @Max(value = 1, message = "{validation.preference.switch.range}")
        Integer enabled) {
}
