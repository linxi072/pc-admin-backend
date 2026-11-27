package com.acme.scaffold.security.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.security.jwt.JwtAuthConverter;
import com.acme.scaffold.security.jwt.JwtProperties;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** OAuth2 用户令牌身份声明回归：tenantId / roles / client_id 必须贯穿签发、兑换与上下文转换。 */
class OAuth2UserTokenTenantClaimTest {

    private static final String CLIENT_ID = "web";
    private static final String REDIRECT = "https://app.example.com/callback";

    private final RsaKeyProvider keys = new RsaKeyProvider(new JwtProperties());
    private final JwtProperties properties = new JwtProperties();
    private final OAuth2TokenProvider provider = new OAuth2TokenProvider(keys, properties);
    private final NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.getPublicKey()).build();

    @Test
    void userTokenShouldCarryTenantRolesAndClientId() {
        String token = provider.issueUserToken(CLIENT_ID, 42L, "alice", 7L, 1,
                Set.of("read"), Set.of("system:user:read"), Set.of("ADMIN"), "jti-1");

        Jwt jwt = decoder.decode(token);
        assertEquals(7L, ((Number) jwt.getClaim("tenantId")).longValue());
        assertEquals("ADMIN", jwt.getClaimAsString("roles"));
        assertEquals(CLIENT_ID, jwt.getClaimAsString("client_id"));

        CurrentPrincipal principal = (CurrentPrincipal) new JwtAuthConverter().convert(jwt).getPrincipal();
        assertEquals(7L, principal.tenantId());
        assertEquals(Set.of("ADMIN"), principal.roles());
        assertEquals(CLIENT_ID, principal.clientId());
    }

    @Test
    void tokenWithoutTenantShouldNotFallBackToDefaultTenantInConverter() {
        String token = provider.issueUserToken(CLIENT_ID, 42L, "alice", null, 1,
                Set.of("read"), Set.of(), Set.of(), "jti-2");

        Jwt jwt = decoder.decode(token);
        assertNull(jwt.getClaim("tenantId"));
        assertNull(((CurrentPrincipal) new JwtAuthConverter().convert(jwt).getPrincipal()).tenantId());
    }

    @Test
    void authorizationCodeShouldRoundTripTenantAndRoles() {
        OAuth2AuthorizationCodeService service = new OAuth2AuthorizationCodeService(keys);
        OAuth2AuthorizationCodeService.CodeContext ctx = new OAuth2AuthorizationCodeService.CodeContext(
                42L, "alice", 1, Set.of("read"), Set.of("ADMIN"), CLIENT_ID, "read", REDIRECT, "", "", 7L);

        String code = service.issue(CLIENT_ID, REDIRECT, ctx);
        OAuth2AuthorizationCodeService.CodeContext consumed = service.consume(code, CLIENT_ID, REDIRECT, null);

        assertEquals(7L, consumed.tenantId());
        assertEquals(Set.of("ADMIN"), consumed.roles());
    }

    @Test
    void passwordGrantShouldPassAuthenticatedTenantIntoToken() {
        OAuth2UserAuthenticator authenticator = mock(OAuth2UserAuthenticator.class);
        when(authenticator.authenticate("alice", "pw")).thenReturn(authenticatedUser());
        OAuth2TokenEndpoint endpoint = endpoint(null, authenticator);

        ResponseEntity<java.util.Map<String, Object>> response = endpoint.token(
                "password", CLIENT_ID, "secret", "alice", "pw", null, null, null, "read", null);

        Jwt jwt = decoder.decode((String) response.getBody().get("access_token"));
        assertEquals(7L, ((Number) jwt.getClaim("tenantId")).longValue());
        assertEquals("ADMIN", jwt.getClaimAsString("roles"));
    }

    @Test
    void authorizationCodeGrantShouldPassConsumedTenantIntoToken() {
        OAuth2AuthorizationCodeService codeService = new OAuth2AuthorizationCodeService(keys);
        String code = codeService.issue(CLIENT_ID, REDIRECT,
                new OAuth2AuthorizationCodeService.CodeContext(
                        42L, "alice", 1, Set.of("read"), Set.of("ADMIN"),
                        CLIENT_ID, "read", REDIRECT, "", "", 7L));
        OAuth2TokenEndpoint endpoint = endpoint(codeService, null);

        ResponseEntity<java.util.Map<String, Object>> response = endpoint.token(
                "authorization_code", CLIENT_ID, "secret", null, null, code, REDIRECT, null, null, null);

        Jwt jwt = decoder.decode((String) response.getBody().get("access_token"));
        assertEquals(7L, ((Number) jwt.getClaim("tenantId")).longValue());
        assertEquals("ADMIN", jwt.getClaimAsString("roles"));
    }

    @Test
    void clientCredentialsTokenShouldRemainTenantless() {
        String token = provider.issueClientCredentialsToken(CLIENT_ID, Set.of("read"), "jti-client");

        Jwt jwt = decoder.decode(token);
        assertNull(jwt.getClaim("tenantId"));
        assertTrue(new JwtAuthConverter().convert(jwt).getPrincipal() instanceof CurrentPrincipal);
    }

    private OAuth2TokenEndpoint endpoint(OAuth2AuthorizationCodeService codeService,
                                         OAuth2UserAuthenticator authenticator) {
        OAuth2ClientDataPort clientPort = mock(OAuth2ClientDataPort.class);
        when(clientPort.authenticate(CLIENT_ID, "secret")).thenReturn(
                new OAuth2ClientView(CLIENT_ID, Set.of("read"), Set.of(), false));
        OAuth2AuthorizationCodeService effectiveCodeService = codeService == null
                ? mock(OAuth2AuthorizationCodeService.class) : codeService;
        OAuth2UserAuthenticator effectiveAuthenticator = authenticator == null
                ? mock(OAuth2UserAuthenticator.class) : authenticator;
        return new OAuth2TokenEndpoint(clientPort, provider, properties, effectiveAuthenticator,
                effectiveCodeService, mock(OAuth2TokenRegistry.class));
    }

    private static OAuth2UserAuthenticator.AuthenticatedUser authenticatedUser() {
        return new OAuth2UserAuthenticator.AuthenticatedUser(
                42L, "alice", "Alice", 7L, 1, Set.of("system:user:read"), Set.of("ADMIN"));
    }
}
