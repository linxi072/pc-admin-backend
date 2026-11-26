package com.acme.scaffold.admin.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.env.MockEnvironment;

/** 生产密钥守卫单测：非空校验、缺失枚举、逃生开关与默认启用。 */
class ProductionSecretsGuardTest {

    @Test
    void guardShouldOnlyRunInProdProfile() {
        Profile profile = ProductionSecretsGuard.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[] {"prod"}, profile.value());
        assertTrue(ApplicationRunner.class.isAssignableFrom(ProductionSecretsGuard.class));
    }

    @Test
    void verifyShouldPassWhenBothSecretsConfigured() {
        MockEnvironment env = environment("base64-jwt-secret", "base64-private-key", true);
        assertDoesNotThrow(() -> new ProductionSecretsGuard(env).verify());
    }

    @Test
    void verifyShouldFailFastWhenJwtSecretMissing() {
        MockEnvironment env = environment(null, "base64-private-key", true);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> new ProductionSecretsGuard(env).verify());
        assertTrue(ex.getMessage().contains("JWT_SECRET"));
        assertTrue(ex.getMessage().contains(ProductionSecretsGuard.ENABLED_PROPERTY));
        assertFalse(ex.getMessage().contains("APP_OAUTH2_PRIVATE_KEY"));
    }

    @Test
    void verifyShouldFailFastWhenOauthPrivateKeyMissing() {
        MockEnvironment env = environment("base64-jwt-secret", null, true);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> new ProductionSecretsGuard(env).verify());
        assertTrue(ex.getMessage().contains("APP_OAUTH2_PRIVATE_KEY"));
        assertFalse(ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void verifyShouldListAllMissingSecrets() {
        MockEnvironment env = environment(null, null, true);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> new ProductionSecretsGuard(env).verify());
        assertTrue(ex.getMessage().contains("JWT_SECRET"));
        assertTrue(ex.getMessage().contains("APP_OAUTH2_PRIVATE_KEY"));
    }

    @Test
    void verifyShouldTreatBlankSecretsAsMissing() {
        MockEnvironment env = environment("   ", "\t", true);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> new ProductionSecretsGuard(env).verify());
        assertTrue(ex.getMessage().contains("JWT_SECRET"));
        assertTrue(ex.getMessage().contains("APP_OAUTH2_PRIVATE_KEY"));
    }

    @Test
    void verifyShouldSkipWhenGuardDisabled() {
        MockEnvironment env = environment(null, null, false);
        assertDoesNotThrow(() -> new ProductionSecretsGuard(env).verify());
    }

    @Test
    void verifyShouldBeEnabledByDefault() {
        MockEnvironment env = new MockEnvironment();
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> new ProductionSecretsGuard(env).verify());
        assertTrue(ex.getMessage().contains("JWT_SECRET"));
        assertTrue(ex.getMessage().contains("APP_OAUTH2_PRIVATE_KEY"));
    }

    private static MockEnvironment environment(String jwtSecret, String oauth2PrivateKey, Boolean enabled) {
        MockEnvironment env = new MockEnvironment();
        if (jwtSecret != null) {
            env.setProperty(ProductionSecretsGuard.JWT_SECRET_PROPERTY, jwtSecret);
        }
        if (oauth2PrivateKey != null) {
            env.setProperty(ProductionSecretsGuard.OAUTH2_PRIVATE_KEY_PROPERTY, oauth2PrivateKey);
        }
        if (enabled != null) {
            env.setProperty(ProductionSecretsGuard.ENABLED_PROPERTY, String.valueOf(enabled));
        }
        return env;
    }
}
