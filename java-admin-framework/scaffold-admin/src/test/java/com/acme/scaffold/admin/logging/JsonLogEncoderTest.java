package com.acme.scaffold.admin.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** JSON 日志编码器单测：标准字段、MDC 透出、字段冲突保护与异常字段。 */
class JsonLogEncoderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void encodeShouldEmitSingleLineJsonWithStandardFields() throws Exception {
        JsonNode node = encode(event("hello", Map.of(), null));
        assertEquals("INFO", node.get("level").asText());
        assertEquals("test.logger", node.get("logger").asText());
        assertEquals("main", node.get("thread").asText());
        assertEquals("hello", node.get("message").asText());
        assertTrue(node.has("@timestamp"));
        assertTrue(node.get("@timestamp").asText().endsWith("Z"));
    }

    @Test
    void mdcFieldsShouldBeExposedVerbatim() throws Exception {
        JsonNode node = encode(event("x", Map.of("traceId", "t-1", "userId", "7"), null));
        assertEquals("t-1", node.get("traceId").asText());
        assertEquals("7", node.get("userId").asText());
    }

    @Test
    void mdcShouldNotOverrideStandardFields() throws Exception {
        JsonNode node = encode(event("real", Map.of("message", "fake", "level", "ERROR"), null));
        assertEquals("real", node.get("message").asText());
        assertEquals("INFO", node.get("level").asText());
    }

    @Test
    void throwableShouldBeFlattenedIntoErrorFields() throws Exception {
        JsonNode node = encode(event("boom", Map.of(), new IllegalStateException("bad state")));
        assertEquals(IllegalStateException.class.getName(), node.get("error.class").asText());
        assertEquals("bad state", node.get("error.message").asText());
    }

    @Test
    void outputShouldEndWithPlatformLineSeparator() {
        String out = new String(new JsonLogEncoder().encode(event("x", Map.of(), null)), StandardCharsets.UTF_8);
        assertTrue(out.endsWith(System.lineSeparator()));
        assertFalse(out.contains("\n" + System.lineSeparator()));
    }

    private static JsonNode encode(LoggingEvent event) throws Exception {
        return JSON.readTree(new JsonLogEncoder().encode(event));
    }

    private static LoggingEvent event(String message, Map<String, String> mdc, Throwable error) {
        LoggingEvent event = new LoggingEvent();
        event.setTimeStamp(1_764_000_000_000L);
        event.setLevel(Level.INFO);
        event.setLoggerName("test.logger");
        event.setThreadName("main");
        event.setMessage(message);
        event.setMDCPropertyMap(mdc);
        if (error != null) {
            event.setThrowableProxy(new ThrowableProxy(error));
        }
        return event;
    }
}
