package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.security.jwt.JwtProperties;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OAuth2 客户端令牌签发（RS256）。
 *
 * <p>仅承载 {@code client_id} / {@code scope} / 标准声明，{@code userId} 为 null，
 * 经复合解码器与现有 HS256 登录令牌共存；{@code userId=null} 自动绕过
 * {@code TokenVersionVerifier} 与 {@code OnlineSessionFilter}（二者均以 userId!=null 为前置守卫）。</p>
 */
@Component
public class OAuth2TokenProvider {

    private final RsaKeyProvider keys;
    private final JwtProperties properties;

    public OAuth2TokenProvider(RsaKeyProvider keys, JwtProperties properties) {
        this.keys = keys;
        this.properties = properties;
    }

    public String issueClientCredentialsToken(String clientId, Set<String> scopes) {
        RSAPrivateKey key = keys.getPrivateKey();
        Date now = new Date();
        long ttlMillis = properties.getOauth2TokenTtl().toMillis();
        Date exp = new Date(now.getTime() + ttlMillis);
        String scope = (scopes == null || scopes.isEmpty())
                ? "read" : scopes.stream().sorted().collect(Collectors.joining(" "));
        return Jwts.builder()
                .setHeaderParam("kid", keys.getKeyId())
                .subject(clientId)
                .issuer(properties.getOauth2Issuer())
                .claim("aud", properties.getOauth2Audience())
                .issuedAt(now)
                .expiration(exp)
                .claim("client_id", clientId)
                .claim("scope", scope)
                .claim("tokenVersion", 1)
                .id(UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.RS256)
                .compact();
    }
}
