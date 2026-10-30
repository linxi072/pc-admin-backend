package com.acme.scaffold.system.notification.rules;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 机器人渠道报文构造（M35）：纯函数、无 Spring 依赖、无副作用，可离线单测。
 *
 * <p>企业微信与钉钉的 {@code text} 消息结构同源，均为
 * <pre>{@code {"msgtype":"text","text":{"content":"<标题>\n<正文>"}}}</pre>
 * 标题与正文以换行拼接（任一段为空则跳过该段）；正文缺省为空串。</p>
 */
public final class RobotPayloads {

    private RobotPayloads() {
    }

    /**
     * 构造 text 报文。
     *
     * @param title   标题（可空；空白不拼入）
     * @param content 正文（可空；空白不拼入）
     * @return 可直接交给 {@code WebhookClient.post(url, body)} 的报文 Map
     */
    public static Map<String, Object> textPayload(String title, String content) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title);
        }
        if (content != null && !content.isBlank()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(content);
        }
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("content", sb.toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "text");
        body.put("text", text);
        return body;
    }
}
