package com.acme.scaffold.security.jwt;

import com.acme.scaffold.security.config.JwtProperties;
import com.acme.scaffold.security.context.CurrentPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 JWT 转换为 Authentication，principal 直接承载 {@link CurrentPrincipal}，便于业务/审计取用。
 * 权限码映射为 SimpleGrantedAuthority，供 {@code @PreAuthorize("hasAuthority('system:user:read')")} 使用。
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final SecretKey key;

    public JwtAuthConverter(JwtProperties properties) {
        this.key = properties.resolveSigningKey();
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Claims claims = parseClaims(jwt.getTokenValue());
        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        String authoritiesStr = claims.get("authorities", String.class);
        String rolesStr = claims.get("roles", String.class);
        Integer tokenVersion = claims.get("tokenVersion", Integer.class);
        String clientId = claims.get("clientId", String.class);

        Set<String> authorities = split(authoritiesStr);
        Set<String> roles = split(rolesStr);

        CurrentPrincipal principal = new CurrentPrincipal(userId, username, username, 0L, clientId, tokenVersion, roles, authorities);
        List<GrantedAuthority> auths = authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        return new UsernamePasswordAuthenticationToken(principal, jwt, auths);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private Set<String> split(String s) {
        if (s == null || s.isBlank()) {
            return new HashSet<>();
        }
        return Arrays.stream(s.split(",")).map(String::trim).filter(t -> !t.isEmpty()).collect(Collectors.toSet());
    }
}
