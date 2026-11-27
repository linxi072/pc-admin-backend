package com.acme.scaffold.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.security.token.service.TokenVersionService;
import io.jsonwebtoken.Jwts;
import java.util.Date;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** WebSocket 握手令牌版本校验回归：强退/禁用后旧令牌不得再建立连接。 */
class JwtHandshakeAuthenticatorTokenVersionTest {

    private final JwtProperties properties = new JwtProperties();
    private final JwtKeyRotationService rotation = new JwtKeyRotationService(properties);
    private final JwtProvider provider = new JwtProvider(properties, rotation);
    private final TokenVersionService versionService = mock(TokenVersionService.class);
    private final JwtHandshakeAuthenticator authenticator =
            new JwtHandshakeAuthenticator(provider, versionService);

    @Test
    void validVersionShouldResolveUserId() {
        String token = provider.generateAccessToken(principal(1));
        when(versionService.isValid(42L, 1)).thenReturn(true);

        assertEquals(42L, authenticator.resolveUserId(token));
    }

    @Test
    void staleVersionShouldRejectHandshake() {
        String token = provider.generateAccessToken(principal(1));
        when(versionService.isValid(42L, 1)).thenReturn(false);

        assertNull(authenticator.resolveUserId(token));
    }

    @Test
    void legacyTokenWithoutVersionShouldUseDefaultVersion() {
        String legacy = Jwts.builder()
                .subject("42")
                .issuer(properties.getIssuer())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(rotation.activeKey(), Jwts.SIG.HS256)
                .compact();
        when(versionService.isValid(42L, null)).thenReturn(true);

        assertEquals(42L, authenticator.resolveUserId(legacy));
    }

    @Test
    void legacyTokenForDeletedUserShouldRejectHandshake() {
        String legacy = Jwts.builder()
                .subject("42")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(rotation.activeKey(), Jwts.SIG.HS256)
                .compact();
        when(versionService.isValid(42L, null)).thenReturn(false);

        assertNull(authenticator.resolveUserId(legacy));
    }

    @Test
    void malformedTokenShouldNotConsultVersionService() {
        assertNull(authenticator.resolveUserId("not-a-jwt"));
        verifyNoInteractions(versionService);
    }

    private static CurrentPrincipal principal(Integer tokenVersion) {
        return new CurrentPrincipal(42L, "alice", "Alice", 7L, null, tokenVersion,
                Set.of("ADMIN"), Set.of("system:user:read"));
    }
}
