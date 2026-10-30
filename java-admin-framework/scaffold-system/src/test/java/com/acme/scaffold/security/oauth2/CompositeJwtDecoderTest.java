package com.acme.scaffold.security.oauth2;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("复合 JWT 解码器")
class CompositeJwtDecoderTest {

    @Test
    @DisplayName("HS256 与 RS256 令牌均可在同一资源服务器校验")
    void decodesBothAlgorithms() throws Exception {
        SecretKey hsKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        JwtDecoder hs256 = NimbusJwtDecoder.withSecretKey(hsKey).build();

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        JwtDecoder rs256 = NimbusJwtDecoder.withPublicKey((RSAPublicKey) kp.getPublic()).build();

        CompositeJwtDecoder composite = new CompositeJwtDecoder(hs256, rs256);

        String hsToken = Jwts.builder().subject("123").signWith(hsKey, Jwts.SIG.HS256).compact();
        Jwt hsJwt = composite.decode(hsToken);
        assertEquals("123", hsJwt.getSubject());

        RSAPrivateKey priv = (RSAPrivateKey) kp.getPrivate();
        String rsToken = Jwts.builder().subject("client-abc")
                .claim("client_id", "client-abc")
                .signWith(priv, Jwts.SIG.RS256).compact();
        Jwt rsJwt = composite.decode(rsToken);
        assertEquals("client-abc", rsJwt.getSubject());
    }

    @Test
    @DisplayName("无法识别的令牌抛出 JwtException")
    void throwsOnGarbage() {
        JwtDecoder hs256 = NimbusJwtDecoder.withSecretKey(
                Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256)).build();
        CompositeJwtDecoder composite = new CompositeJwtDecoder(hs256);
        assertThrows(JwtException.class, () -> composite.decode("not-a-jwt"));
    }
}
