package com.acme.scaffold.security.jwt;

import com.acme.scaffold.common.context.CurrentPrincipal;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 JWT 转换为 Authentication，principal 直接承载 {@link CurrentPrincipal}，便于业务/审计取用。
 * 权限码映射为 SimpleGrantedAuthority，供 {@code @PreAuthorize("hasAuthority('system:user:read')")} 使用。
 *
 * <p><b>算法无关</b>：不再用密钥重解原始 token（旧实现仅支持 HS256），直接读取已由解码器
 * 校验过的 {@link Jwt} 对象声明。subject 为纯数字时视为登录用户（userId=该值）；非数字
 * （如 OAuth2 client_credentials 的 sub=client_id）时 userId=null、username 回落 clientId，
 * 并把 scope 声明映射为 {@code SCOPE_<scope>} 权限，供 {@code hasAuthority('SCOPE_read')} 使用。</p>
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String subject = jwt.getSubject();
        Long userId;
        String username;
        if (subject != null && subject.matches("\\d+")) {
            userId = Long.valueOf(subject);
            username = jwt.getClaimAsString("username");
            if (username == null) {
                username = "";
            }
        } else {
            // 非数字 subject：OAuth2 客户端令牌（sub=client_id），无用户身份
            username = jwt.getClaimAsString("clientId");
            if (username == null || username.isBlank()) {
                username = subject != null ? subject : "";
            }
            userId = null;
        }

        String authoritiesStr = jwt.getClaimAsString("authorities");
        String rolesStr = jwt.getClaimAsString("roles");
        String scopeStr = jwt.getClaimAsString("scope");

        Set<String> authorities = new HashSet<>();
        if (authoritiesStr != null) {
            authorities.addAll(split(authoritiesStr));
        }
        if (scopeStr != null) {
            for (String s : scopeStr.trim().split("[,;\\s]+")) {
                if (!s.isEmpty()) {
                    authorities.add("SCOPE_" + s);
                }
            }
        }
        Set<String> roles = split(rolesStr);

        Integer tokenVersion = jwt.getClaim("tokenVersion") != null
                ? jwt.getClaim("tokenVersion") : 1;
        String clientId = jwt.getClaimAsString("clientId");

        CurrentPrincipal principal = new CurrentPrincipal(
                userId, username, username, 0L, clientId, tokenVersion, roles, authorities);
        List<GrantedAuthority> auths = authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        return new UsernamePasswordAuthenticationToken(principal, jwt, auths);
    }

    private static Set<String> split(String s) {
        if (s == null || s.isBlank()) {
            return new HashSet<>();
        }
        return Arrays.stream(s.split(",")).map(String::trim).filter(t -> !t.isEmpty()).collect(Collectors.toSet());
    }
}
