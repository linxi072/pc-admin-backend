package com.acme.scaffold.security.jwt;

import com.acme.scaffold.security.config.JwtProperties;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.jwt.JwtKeyRotationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * Access Token 签发与解析。使用 HS256，密钥来自 {@link JwtKeyRotationService}（支持密钥轮换）。
 * 仅承载非敏感声明：用户 ID、用户名、权限码、角色、tokenVersion。
 */
@Component
public class JwtProvider {

    private final JwtProperties properties;
    private final JwtKeyRotationService rotation;
    private final SecretKey key;

    public JwtProvider(JwtProperties properties, JwtKeyRotationService rotation) {
        this.properties = properties;
        this.rotation = rotation;
        this.key = rotation.activeKey();
    }

    public String generateAccessToken(CurrentPrincipal principal) {
        long ttlMillis = properties.getAccessTokenTtl().toMillis();
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttlMillis);
        String authorities = principal.permissions() == null ? "" : String.join(",", principal.permissions());
        String roles = principal.roles() == null ? "" : String.join(",", principal.roles());
        return Jwts.builder()
                .setHeaderParam("kid", rotation.activeKid())
                .subject(String.valueOf(principal.userId()))
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiration(exp)
                .claim("username", principal.username())
                .claim("authorities", authorities)
                .claim("roles", roles)
                .claim("tokenVersion", principal.tokenVersion() == null ? 1 : principal.tokenVersion())
                .claim("clientId", principal.clientId())
                .claim("aud", properties.getAudience())
                .id(UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .keyLocator(header -> {
                    Object kid = header.get("kid");
                    return rotation.keyFor(kid == null ? null : kid.toString()).orElse(null);
                })
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
