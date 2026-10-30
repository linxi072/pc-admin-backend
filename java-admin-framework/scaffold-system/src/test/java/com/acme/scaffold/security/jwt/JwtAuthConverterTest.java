package com.acme.scaffold.security.jwt;

import com.acme.scaffold.common.context.CurrentPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JwtAuthConverter} 回归测试。
 *
 * <p>重点防守：JWT 解码后数值声明（如 {@code tokenVersion}）被 Nimbus/Jackson 表示为 {@code Long}，
 * 若以 {@code (Integer)} 强转会触发 {@link ClassCastException} 并令所有鉴权请求返回 500。
 * 本测试以 {@code 1L}（Long）模拟真实解码结果，确保转换器对其稳健处理。</p>
 */
@DisplayName("JWT -> Authentication 转换（JwtAuthConverter）")
class JwtAuthConverterTest {

    private static Jwt.Builder base(String subject) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject);
    }

    private static CurrentPrincipal principalOf(Jwt jwt) {
        JwtAuthConverter converter = new JwtAuthConverter();
        return (CurrentPrincipal) converter.convert(jwt).getPrincipal();
    }

    @Test
    @DisplayName("登录令牌：数值 subject 解析 userId，tokenVersion 为 Long(1L) 时不抛 ClassCastException")
    void loginToken_longTokenVersion_converts() {
        Jwt jwt = base("123")
                .claim("username", "alice")
                .claim("authorities", "system:user:read,system:user:write")
                .claim("roles", "ADMIN")
                .claim("tokenVersion", 1L) // 模拟真实解码后的 Long
                .build();

        CurrentPrincipal p = principalOf(jwt);
        assertNotNull(p);
        assertEquals(123L, p.userId());
        assertEquals("alice", p.username());
        assertEquals(1, p.tokenVersion());
        assertTrue(p.permissions().contains("system:user:read"));
        assertTrue(p.roles().contains("ADMIN"));
    }

    @Test
    @DisplayName("tokenVersion 为 Integer(1) 时同样正确转换")
    void integerTokenVersion_converts() {
        Jwt jwt = base("456")
                .claim("username", "bob")
                .claim("tokenVersion", 1)
                .build();

        CurrentPrincipal p = principalOf(jwt);
        assertEquals(456L, p.userId());
        assertEquals(1, p.tokenVersion());
    }

    @Test
    @DisplayName("tokenVersion 声明缺失时回落为 1（兼容历史 token）")
    void missingTokenVersion_defaultsTo1() {
        Jwt jwt = base("789")
                .claim("username", "carol")
                .build();

        CurrentPrincipal p = principalOf(jwt);
        assertEquals(789L, p.userId());
        assertEquals(1, p.tokenVersion());
    }

    @Test
    @DisplayName("非数字 subject（OAuth2 客户端令牌）：userId 为 null，username 回落 clientId，scope 映射为 SCOPE_* 权限")
    void clientToken_clientIdFallback_andScopeAuthorities() {
        Jwt jwt = base("client-x")
                .claim("clientId", "client-x")
                .claim("scope", "read write")
                .claim("tokenVersion", 1L)
                .build();

        JwtAuthConverter converter = new JwtAuthConverter();
        Authentication auth = converter.convert(jwt);
        CurrentPrincipal p = (CurrentPrincipal) auth.getPrincipal();

        assertNull(p.userId());
        assertEquals("client-x", p.username());
        assertEquals(1, p.tokenVersion());

        Collection<? extends GrantedAuthority> auths =
                ((AbstractAuthenticationToken) auth).getAuthorities();
        boolean hasRead = auths.stream().anyMatch(a -> "SCOPE_read".equals(a.getAuthority()));
        boolean hasWrite = auths.stream().anyMatch(a -> "SCOPE_write".equals(a.getAuthority()));
        assertTrue(hasRead, "应映射 SCOPE_read");
        assertTrue(hasWrite, "应映射 SCOPE_write");
    }
}
