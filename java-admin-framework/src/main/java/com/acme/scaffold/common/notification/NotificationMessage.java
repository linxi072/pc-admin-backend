package com.acme.scaffold.common.notification;

import java.util.List;
import java.util.Map;

/**
 * 渠道无关的通知消息载体。调用方按目标渠道填充对应字段：
 * <ul>
 *   <li>{@code IN_APP}：填 {@code receiverIds} / {@code roleIds} / {@code orgIds}（与站内信一致）；</li>
 *   <li>{@code WEBHOOK}：填 {@code webhookUrl}（可选，缺省走配置默认地址），{@code extra} 携带业务自定义字段；</li>
 * </ul>
 */
public record NotificationMessage(
        Channel channel,
        String title,
        String content,
        String msgType,
        List<Long> receiverIds,
        List<Long> roleIds,
        List<Long> orgIds,
        String webhookUrl,
        Map<String, Object> extra) {

    /** 不含渠道特有字段的便捷构造（用于单渠道快速投递）。 */
    public NotificationMessage(Channel channel, String title, String content) {
        this(channel, title, content, null, null, null, null, null, null);
    }
}
