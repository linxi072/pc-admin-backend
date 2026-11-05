package com.acme.scaffold.admin.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.encoder.EncoderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 零依赖 JSON 日志编码器（P1-9）：将每条日志序列化为单行 JSON，
 * 含 {@code @timestamp / level / logger / thread / message} 标准字段，
 * 并把 MDC 中的 {@code traceId / spanId / userId} 等结构化字段透出，
 * 便于 ELK / Loki 等集中采集与按 trace 串联。
 *
 * <p>仅依赖 Spring Boot 既有的 Jackson（不引入 logstash-logback-encoder，避免离线构建缺构件）；
 * 序列化失败兜底为最小 JSON，绝不因日志格式化异常中断业务线程。</p>
 */
public class JsonLogEncoder extends EncoderBase<ILoggingEvent> {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT;

    @Override
    public byte[] encode(ILoggingEvent event) {
        ObjectNode node = mapper.createObjectNode();
        node.put("@timestamp",
                TS.format(Instant.ofEpochMilli(event.getTimeStamp()).atZone(ZoneOffset.UTC)));
        node.put("level", event.getLevel().toString());
        node.put("logger", event.getLoggerName());
        node.put("thread", event.getThreadName());
        node.put("message", event.getFormattedMessage());
        // MDC 结构化字段（traceId / spanId / userId 等）原样透出，避免与上面标准字段冲突
        if (event.getMDCPropertyMap() != null) {
            event.getMDCPropertyMap().forEach((k, v) -> {
                if (!"@timestamp".equals(k) && !"level".equals(k)
                        && !"logger".equals(k) && !"thread".equals(k) && !"message".equals(k)) {
                    node.put(k, v);
                }
            });
        }
        IThrowableProxy tp = event.getThrowableProxy();
        if (tp != null) {
            node.put("error.class", tp.getClassName());
            node.put("error.message", tp.getMessage());
        }
        try {
            return (mapper.writeValueAsString(node) + System.lineSeparator())
                    .getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 兜底：日志本身不应导致业务失败
            String safe = event.getFormattedMessage() == null
                    ? "" : event.getFormattedMessage().replace("\"", "'");
            return ("{\"level\":\"" + event.getLevel() + "\",\"message\":\"" + safe + "\"}\n")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public byte[] headerBytes() {
        return null;
    }

    @Override
    public byte[] footerBytes() {
        return null;
    }
}
