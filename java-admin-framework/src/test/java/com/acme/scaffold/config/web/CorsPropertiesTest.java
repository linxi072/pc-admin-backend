package com.acme.scaffold.config.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorsPropertiesTest {

    @Test
    void defaultIsAllowAll() {
        CorsProperties properties = new CorsProperties();
        assertEquals(List.of("*"), properties.getAllowedOrigins());
    }

    @Test
    void nullFallsBackToAllowAll() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(null);
        assertEquals(List.of("*"), properties.getAllowedOrigins());
    }

    @Test
    void emptyFallsBackToAllowAll() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of());
        assertEquals(List.of("*"), properties.getAllowedOrigins());
    }

    @Test
    void explicitOriginsRetained() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("https://admin.example.com", "https://app.example.com"));
        assertEquals(List.of("https://admin.example.com", "https://app.example.com"),
                properties.getAllowedOrigins());
    }
}
