package com.acme.scaffold.security.secret;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSecretProviderTest {

    @Test
    void returnsConfigured() {
        LocalSecretProvider p = new LocalSecretProvider(
                Map.of("jwt-secret", "A", "previous-jwt-secret", "B"));
        assertEquals("A", p.get("jwt-secret").orElse(null));
        assertEquals("B", p.get("previous-jwt-secret").orElse(null));
    }

    @Test
    void missing_empty() {
        LocalSecretProvider p = new LocalSecretProvider(Map.of());
        assertTrue(p.get("x").isEmpty());
    }
}
