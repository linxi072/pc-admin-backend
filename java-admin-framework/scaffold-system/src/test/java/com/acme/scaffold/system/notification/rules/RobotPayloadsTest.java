package com.acme.scaffold.system.notification.rules;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RobotPayloads} 测试：企业微信 / 钉钉同源 text 报文结构
 * {@code {"msgtype":"text","text":{"content":"<标题>\n<正文>"}}}。
 */
class RobotPayloadsTest {

    @Test
    @SuppressWarnings("unchecked")
    void textPayloadJoinsTitleAndContentWithNewline() {
        Map<String, Object> body = RobotPayloads.textPayload("告警", "服务不可用");
        assertEquals("text", body.get("msgtype"));
        assertInstanceOf(Map.class, body.get("text"));
        Map<String, Object> text = (Map<String, Object>) body.get("text");
        assertEquals("告警\n服务不可用", text.get("content"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void textPayloadTitleOnly() {
        Map<String, Object> body = RobotPayloads.textPayload("仅标题", null);
        Map<String, Object> text = (Map<String, Object>) body.get("text");
        assertEquals("仅标题", text.get("content"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void textPayloadContentOnly() {
        Map<String, Object> body = RobotPayloads.textPayload(null, "仅正文");
        Map<String, Object> text = (Map<String, Object>) body.get("text");
        assertEquals("仅正文", text.get("content"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void textPayloadEmptyYieldsBlankContent() {
        Map<String, Object> body = RobotPayloads.textPayload(null, null);
        Map<String, Object> text = (Map<String, Object>) body.get("text");
        assertTrue(text.get("content") instanceof String);
        assertEquals("", text.get("content"));
    }
}
