package com.acme.scaffold.observability.export;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtlpSpanExporterTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> received = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            received.set(new String(body, StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsOtlpJsonWhenEnabled() {
        OtlpSpanExporter exporter = new OtlpSpanExporter(true,
                "http://127.0.0.1:" + port + "/v1/traces", "svc", 2000);
        OtlpSpan span = new OtlpSpan("t", "s", null, "x",
                OtlpSpan.Kind.SERVER, 0, 1, List.of(), OtlpSpan.StatusCode.OK, null);
        exporter.export(List.of(span));

        assertNotNull(received.get());
        assertTrue(received.get().contains("\"traceId\":\"t\""), received.get());
        assertTrue(received.get().contains("resourceSpans"), received.get());
    }

    @Test
    void noopWhenDisabled() {
        OtlpSpanExporter exporter = new OtlpSpanExporter(false,
                "http://127.0.0.1:" + port + "/v1/traces", "svc", 2000);
        exporter.export(List.of(new OtlpSpan("t", "s", null, "x",
                OtlpSpan.Kind.SERVER, 0, 1, List.of(), OtlpSpan.StatusCode.OK, null)));
        assertNull(received.get());
    }

    @Test
    void doesNotThrowOnUnreachableEndpoint() {
        OtlpSpanExporter exporter = new OtlpSpanExporter(true,
                "http://127.0.0.1:1/v1/traces", "svc", 200);
        // 不可达端点：应记录日志且不抛异常
        exporter.export(List.of(new OtlpSpan("t", "s", null, "x",
                OtlpSpan.Kind.SERVER, 0, 1, List.of(), OtlpSpan.StatusCode.OK, null)));
    }
}
