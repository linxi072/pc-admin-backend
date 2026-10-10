package com.acme.scaffold.security.jwt;

import com.acme.scaffold.security.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtKeyRotationServiceTest {

    private static String randomBase64Key() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    private JwtKeyRotationService rotationWithTwoKeys() {
        JwtProperties props = new JwtProperties();
        props.setJwtSecret(randomBase64Key());
        props.setPreviousJwtSecret(randomBase64Key());
        props.setKeyId("1");
        props.setPreviousKeyId("0");
        return new JwtKeyRotationService(props);
    }

    private JwtDecoder decoder(JwtKeyRotationService rotation) {
        JwtDecoder active = NimbusJwtDecoder.withSecretKey(rotation.activeKey()).build();
        JwtDecoder previous = rotation.previousKey()
                .map(k -> (JwtDecoder) NimbusJwtDecoder.withSecretKey(k).build())
                .orElse(null);
        return previous == null ? new RotatingJwtDecoder(active) : new RotatingJwtDecoder(active, previous);
    }

    private String signWith(SecretKey key, String kid, String subject) {
        return Jwts.builder().setHeaderParam("kid", kid).subject(subject)
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    @Test
    void keyFor_resolvesByKid() {
        JwtKeyRotationService rotation = rotationWithTwoKeys();
        assertTrue(rotation.keyFor(null).isPresent());
        assertTrue(rotation.keyFor("1").isPresent());
        assertTrue(rotation.keyFor("0").isPresent());
        assertFalse(rotation.keyFor("999").isPresent());
    }

    @Test
    void decode_activeKeyToken_succeeds() {
        JwtKeyRotationService rotation = rotationWithTwoKeys();
        String token = signWith(rotation.activeKey(), rotation.activeKid(), "u1");
        Jwt jwt = decoder(rotation).decode(token);
        assertEquals("u1", jwt.getSubject());
    }

    @Test
    void decode_previousKeyToken_succeedsDuringRotation() {
        JwtKeyRotationService rotation = rotationWithTwoKeys();
        SecretKey previousKey = rotation.keyFor("0").get();
        String token = signWith(previousKey, "0", "u2");
        // 轮换过渡期：旧密钥签发的 token 依然可被解码
        Jwt jwt = decoder(rotation).decode(token);
        assertEquals("u2", jwt.getSubject());
    }

    @Test
    void decode_foreignKeyToken_rejected() {
        JwtKeyRotationService rotation = rotationWithTwoKeys();
        // 既不是当前密钥也不是上一版本密钥签发的 token，应被拒绝
        byte[] foreign = new byte[32];
        new SecureRandom().nextBytes(foreign);
        SecretKey foreignKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(foreign);
        String token = signWith(foreignKey, "zzz", "u3");
        assertThrows(JwtException.class, () -> decoder(rotation).decode(token));
    }

    @Test
    void noPreviousSecret_prevKidEmpty() {
        JwtProperties props = new JwtProperties();
        props.setJwtSecret(randomBase64Key());
        props.setKeyId("1");
        props.setPreviousKeyId("0");
        JwtKeyRotationService rotation = new JwtKeyRotationService(props);
        assertTrue(rotation.keyFor("1").isPresent());
        // 没有上一版本密钥时，prev kid 解析为空
        Optional<SecretKey> prev = rotation.keyFor("0");
        assertFalse(prev.isPresent());
    }
}
