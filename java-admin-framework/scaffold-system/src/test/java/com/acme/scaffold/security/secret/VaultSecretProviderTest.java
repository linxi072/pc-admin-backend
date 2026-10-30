package com.acme.scaffold.security.secret;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultSecretProviderTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/secret/data/admin/jwt", ex -> {
            byte[] b = "{\"data\":{\"data\":{\"value\":\"BASE64SECRET\"}}}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.createContext("/v1/secret/data/admin/jwt-previous", ex -> {
            byte[] b = "{\"data\":{\"data\":{\"value\":\"PREVSECRET\"}}}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
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
    void returnsValueForKnownPath() {
        VaultSecretProvider p = new VaultSecretProvider("http://127.0.0.1:" + port, "tok",
                Map.of("jwt-secret", "secret/data/admin/jwt",
                        "previous-jwt-secret", "secret/data/admin/jwt-previous"));
        assertEquals("BASE64SECRET", p.get("jwt-secret").orElse(null));
        assertEquals("PREVSECRET", p.get("previous-jwt-secret").orElse(null));
    }

    @Test
    void unknownLogicalKey_empty() {
        VaultSecretProvider p = new VaultSecretProvider("http://127.0.0.1:" + port, "tok", Map.of());
        assertTrue(p.get("nope").isEmpty());
    }

    @Test
    void serverError_empty() {
        VaultSecretProvider p = new VaultSecretProvider("http://127.0.0.1:" + port, "tok",
                Map.of("jwt-secret", "secret/data/missing"));
        assertTrue(p.get("jwt-secret").isEmpty());
    }

    @Test
    void unreachable_emptyNotThrow() {
        VaultSecretProvider p = new VaultSecretProvider("http://127.0.0.1:1", "tok",
                Map.of("jwt-secret", "secret/data/admin/jwt"));
        assertTrue(p.get("jwt-secret").isEmpty());
    }
}
