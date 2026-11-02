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

    /**
     * 签发「代表资源拥有者」的 OAuth2 令牌（{@code password} / {@code authorization_code} 授权模式使用）。
     *
     * <p>与客户端令牌的差异：
     * <ul>
     *   <li>{@code sub} 为数值 userId，使 {@link com.acme.scaffold.security.jwt.JwtAuthConverter}
     *       识别为用户身份（携带真实 {@code authorities} 权限码，可访问 {@code system:*} 受保护接口）；</li>
     *   <li>携带 {@code username} / {@code tokenVersion} / {@code authorities} 声明，
     *       其中 {@code tokenVersion} 与库中版本对齐，使 Token 版本校验过滤器在改密后正确失效；</li>
     *   <li>{@code OnlineSessionFilter} 以「无 X-Device-Id 头」为前置守卫，机器客户端不发送该头，
     *       故不会因缺少在线会话而被误拒。</li>
     * </ul>
     */
    public String issueUserToken(String clientId, Long userId, String username, int tokenVersion,
                                 Set<String> scopes, Set<String> authorities) {
        RSAPrivateKey key = keys.getPrivateKey();
        Date now = new Date();
        long ttlMillis = properties.getOauth2TokenTtl().toMillis();
        Date exp = new Date(now.getTime() + ttlMillis);
        String scope = (scopes == null || scopes.isEmpty())
                ? "read" : scopes.stream().sorted().collect(Collectors.joining(" "));
        String auth = (authorities == null || authorities.isEmpty())
                ? "" : authorities.stream().sorted().collect(Collectors.joining(","));
        return Jwts.builder()
                .setHeaderParam("kid", keys.getKeyId())
                .subject(String.valueOf(userId))
                .issuer(properties.getOauth2Issuer())
                .claim("aud", properties.getOauth2Audience())
                .issuedAt(now)
                .expiration(exp)
                .claim("client_id", clientId)
                .claim("scope", scope)
                .claim("authorities", auth)
                .claim("username", username)
                .claim("tokenVersion", tokenVersion)
                .id(UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 签发客户端令牌（jti 由调用方透传，便于 {@code OAuth2TokenEndpoint} 落库注册表）。
     * 其余语义与 {@link #issueClientCredentialsToken(String, Set)} 一致。
     */
    public String issueClientCredentialsToken(String clientId, Set<String> scopes, String jti) {
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
                .id(jti)
                .signWith(key, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 签发代表资源拥有者的 OAuth2 令牌（jti 由调用方透传，便于 {@code OAuth2TokenEndpoint} 落库注册表）。
     * 其余语义与 {@link #issueUserToken(String, Long, String, int, Set, Set)} 一致。
     */
    public String issueUserToken(String clientId, Long userId, String username, int tokenVersion,
                                 Set<String> scopes, Set<String> authorities, String jti) {
        RSAPrivateKey key = keys.getPrivateKey();
        Date now = new Date();
        long ttlMillis = properties.getOauth2TokenTtl().toMillis();
        Date exp = new Date(now.getTime() + ttlMillis);
        String scope = (scopes == null || scopes.isEmpty())
                ? "read" : scopes.stream().sorted().collect(Collectors.joining(" "));
        String auth = (authorities == null || authorities.isEmpty())
                ? "" : authorities.stream().sorted().collect(Collectors.joining(","));
        return Jwts.builder()
                .setHeaderParam("kid", keys.getKeyId())
                .subject(String.valueOf(userId))
                .issuer(properties.getOauth2Issuer())
                .claim("aud", properties.getOauth2Audience())
                .issuedAt(now)
                .expiration(exp)
                .claim("client_id", clientId)
                .claim("scope", scope)
                .claim("authorities", auth)
                .claim("username", username)
                .claim("tokenVersion", tokenVersion)
                .id(jti)
                .signWith(key, Jwts.SIG.RS256)
                .compact();
    }
}
