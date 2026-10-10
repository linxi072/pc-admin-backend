package com.acme.scaffold.security.secret;

import com.acme.scaffold.security.config.JwtProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecretResolverTest {

    @Test
    void resolvesIntoJwtProperties() {
        JwtProperties jwt = new JwtProperties();
        SecretProvider provider = new LocalSecretProvider(
                Map.of("jwt-secret", "SEC", "previous-jwt-secret", "PREV"));
        SecretResolver.resolveJwtSecrets(provider, jwt);
        assertEquals("SEC", jwt.getJwtSecret());
        assertEquals("PREV", jwt.getPreviousJwtSecret());
    }

    @Test
    void leavesUnresolvedUntouched() {
        JwtProperties jwt = new JwtProperties();
        jwt.setJwtSecret("EXISTING");
        SecretProvider provider = new LocalSecretProvider(Map.of());
        SecretResolver.resolveJwtSecrets(provider, jwt);
        assertEquals("EXISTING", jwt.getJwtSecret());
    }
}
