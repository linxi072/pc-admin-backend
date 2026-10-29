package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.security.error.SecurityErrorCode;
import io.jsonwebtoken.Jwts;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OAuth2 {@code authorization_code} 授权码签发 / 兑换（一次性、短时效）。
 *
 * <p>授权码为 RS256 签名的 JWT（承载 client_id / redirect_uri / scope / 资源拥有者身份与权限），
 * 由 {@link RsaKeyProvider} 私钥签发、公钥解码（复用同一密钥对，与访问令牌同源）。
 * 兑换时校验签名/过期后，以内存 {@code usedJtis} 保证一次性使用（防重放），并比对
 * client_id 与 redirect_uri 与授权时一致。</p>
 *
 * <p>内存态一次性集合：重启即失效，符合授权码「极短生命周期」语义；生产多实例可替换为共享缓存，
 * 当前离线优先、零新依赖。</p>
 */
@Component
public class OAuth2AuthorizationCodeService {

    /** 授权码有效期。RFC 建议尽量短，默认 60 秒。 */
    private static final Duration CODE_TTL = Duration.ofSeconds(60);

    private final RsaKeyProvider keys;
    private final NimbusJwtDecoder rs256Decoder;
    private final Set<String> usedJtis = ConcurrentHashMap.newKeySet();

    public OAuth2AuthorizationCodeService(RsaKeyProvider keys) {
        this.keys = keys;
        this.rs256Decoder = NimbusJwtDecoder.withPublicKey(keys.getPublicKey()).build();
    }

    public record CodeContext(Long userId, String username, int tokenVersion,
                              Set<String> authorities, Set<String> roles,
                              String clientId, String scope, String redirectUri) {
    }

    /** 签发授权码（RS256 签名 JWT，绑定 client/redirect/scope 与资源拥有者上下文）。 */
    public String issue(String clientId, String redirectUri, CodeContext ctx) {
        RSAPrivateKey key = keys.getPrivateKey();
        Date now = new Date();
        Date exp = new Date(now.getTime() + CODE_TTL.toMillis());
        String scope = ctx.scope() == null ? "" : ctx.scope();
        String authorities = ctx.authorities() == null ? "" : String.join(",", ctx.authorities());
        String roles = ctx.roles() == null ? "" : String.join(",", ctx.roles());
        return Jwts.builder()
                .setHeaderParam("kid", keys.getKeyId())
                .subject(ctx.userId().toString())
                .claim("client_id", clientId)
                .claim("redirect_uri", redirectUri == null ? "" : redirectUri)
                .claim("scope", scope)
                .claim("username", ctx.username())
                .claim("authorities", authorities)
                .claim("roles", roles)
                .claim("tokenVersion", ctx.tokenVersion())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 校验并消费（一次性）授权码，返回承载的上下文。
     *
     * @throws BusinessException 授权码无效 / 过期 / 已被使用 / 客户端或 redirect_uri 不匹配
     */
    public CodeContext consume(String code, String clientId, String redirectUri) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(SecurityErrorCode.OAUTH2_INVALID_GRANT, "授权码缺失");
        }
        Jwt jwt;
        try {
            jwt = rs256Decoder.decode(code);
        } catch (JwtException e) {
            throw new BusinessException(SecurityErrorCode.OAUTH2_INVALID_GRANT, "授权码无效或已过期");
        }
        String jti = jwt.getId();
        if (jti == null || usedJtis.contains(jti)) {
            throw new BusinessException(SecurityErrorCode.OAUTH2_INVALID_GRANT, "授权码已被使用");
        }
        String jwtClientId = jwt.getClaimAsString("client_id");
        if (!clientId.equals(jwtClientId)) {
            throw new BusinessException(SecurityErrorCode.OAUTH2_INVALID_GRANT, "授权码与客户端不匹配");
        }
        String jwtRedirect = jwt.getClaimAsString("redirect_uri");
        if (jwtRedirect == null) {
            jwtRedirect = "";
        }
        String reqRedirect = redirectUri == null ? "" : redirectUri;
        if (!jwtRedirect.equals(reqRedirect)) {
            throw new BusinessException(SecurityErrorCode.OAUTH2_INVALID_GRANT, "redirect_uri 不匹配");
        }
        // 全部校验通过后才标记已消费，保证非法请求不消耗授权码（仍可重试至过期）
        usedJtis.add(jti);

        Long userId = Long.valueOf(jwt.getSubject());
        int tokenVersion = jwt.getClaim("tokenVersion") instanceof Number n ? n.intValue() : 1;
        Set<String> authorities = split(jwt.getClaimAsString("authorities"));
        Set<String> roles = split(jwt.getClaimAsString("roles"));
        String scope = jwt.getClaimAsString("scope");
        return new CodeContext(userId, jwt.getClaimAsString("username"), tokenVersion,
                authorities, roles, clientId, scope, jwtRedirect);
    }

    private static Set<String> split(String s) {
        if (s == null || s.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String p : s.split(",")) {
            p = p.trim();
            if (!p.isEmpty()) {
                set.add(p);
            }
        }
        return set;
    }
}
