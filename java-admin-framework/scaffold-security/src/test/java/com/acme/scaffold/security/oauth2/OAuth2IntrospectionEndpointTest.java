package com.acme.scaffold.security.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/** OAuth2 自省端点调用方鉴权回归测试：畸形 Basic 头必须按未认证处理，不能穿透为 500。 */
class OAuth2IntrospectionEndpointTest {

    @Test
    void malformedBasicHeaderShouldReturnUnauthorizedInsteadOf500() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        OAuth2ClientDataPort clientPort = mock(OAuth2ClientDataPort.class);
        OAuth2IntrospectionEndpoint endpoint = endpoint(decoder, clientPort);

        ResponseEntity<Map<String, Object>> response = endpoint.introspect("jwt-token", "Basic !!!");

        assertError(response, HttpStatus.UNAUTHORIZED, "invalid_client");
        verifyNoInteractions(decoder, clientPort);
    }

    @Test
    void basicHeaderWithoutColonShouldReturnUnauthorized() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        OAuth2ClientDataPort clientPort = mock(OAuth2ClientDataPort.class);
        OAuth2IntrospectionEndpoint endpoint = endpoint(decoder, clientPort);

        ResponseEntity<Map<String, Object>> response = endpoint.introspect("jwt-token", basic("client-only"));

        assertError(response, HttpStatus.UNAUTHORIZED, "invalid_client");
        verifyNoInteractions(clientPort);
    }

    @Test
    void wrongBasicCredentialsShouldReturnUnauthorized() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        OAuth2ClientDataPort clientPort = mock(OAuth2ClientDataPort.class);
        when(clientPort.authenticate("client", "bad"))
                .thenThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED, "客户端认证失败"));
        OAuth2IntrospectionEndpoint endpoint = endpoint(decoder, clientPort);

        ResponseEntity<Map<String, Object>> response = endpoint.introspect("jwt-token", basic("client:bad"));

        assertError(response, HttpStatus.UNAUTHORIZED, "invalid_client");
        verify(decoder, never()).decode(anyString());
    }

    @Test
    void missingAuthorizationShouldReturnUnauthorized() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        OAuth2ClientDataPort clientPort = mock(OAuth2ClientDataPort.class);
        OAuth2IntrospectionEndpoint endpoint = endpoint(decoder, clientPort);

        ResponseEntity<Map<String, Object>> response = endpoint.introspect("jwt-token", null);

        assertError(response, HttpStatus.UNAUTHORIZED, "invalid_client");
        verifyNoInteractions(decoder, clientPort);
    }

    @Test
    void validBasicCredentialsShouldReturnActiveResponse() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        OAuth2ClientDataPort clientPort = mock(OAuth2ClientDataPort.class);
        when(clientPort.authenticate("client", "secret")).thenReturn(client());
        when(decoder.decode("jwt-token")).thenReturn(jwt());
        OAuth2IntrospectionEndpoint endpoint = endpoint(decoder, clientPort);

        ResponseEntity<Map<String, Object>> response = endpoint.introspect("jwt-token", basic("client:secret"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Boolean.TRUE, response.getBody().get("active"));
        assertEquals("Bearer", response.getBody().get("token_type"));
        assertEquals("read write", response.getBody().get("scope"));
        assertEquals("client", response.getBody().get("client_id"));
        assertEquals("alice", response.getBody().get("username"));
        verify(clientPort).authenticate("client", "secret");
    }

    @Test
    void validBearerCallerShouldReturnActiveResponse() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        OAuth2ClientDataPort clientPort = mock(OAuth2ClientDataPort.class);
        when(decoder.decode("caller-token")).thenReturn(jwt());
        when(decoder.decode("jwt-token")).thenReturn(jwt());
        OAuth2IntrospectionEndpoint endpoint = endpoint(decoder, clientPort);

        ResponseEntity<Map<String, Object>> response = endpoint.introspect("jwt-token", "Bearer caller-token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Boolean.TRUE, response.getBody().get("active"));
        verify(decoder).decode("caller-token");
        verify(decoder).decode("jwt-token");
        verifyNoInteractions(clientPort);
    }

    @Test
    void invalidBearerCallerShouldReturnUnauthorized() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        OAuth2ClientDataPort clientPort = mock(OAuth2ClientDataPort.class);
        when(decoder.decode("bad-caller")).thenThrow(new JwtException("invalid caller token"));
        OAuth2IntrospectionEndpoint endpoint = endpoint(decoder, clientPort);

        ResponseEntity<Map<String, Object>> response = endpoint.introspect("jwt-token", "Bearer bad-caller");

        assertError(response, HttpStatus.UNAUTHORIZED, "invalid_client");
        verifyNoInteractions(clientPort);
    }

    @Test
    void authenticatedCallerWithBlankTokenShouldReturnBadRequest() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        OAuth2ClientDataPort clientPort = mock(OAuth2ClientDataPort.class);
        when(clientPort.authenticate("client", "secret")).thenReturn(client());
        OAuth2IntrospectionEndpoint endpoint = endpoint(decoder, clientPort);

        ResponseEntity<Map<String, Object>> response = endpoint.introspect("  ", basic("client:secret"));

        assertError(response, HttpStatus.BAD_REQUEST, "invalid_request");
        verify(decoder, never()).decode(anyString());
    }

    @Test
    void authenticatedCallerWithInvalidTokenShouldReturnInactive() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        OAuth2ClientDataPort clientPort = mock(OAuth2ClientDataPort.class);
        when(clientPort.authenticate("client", "secret")).thenReturn(client());
        when(decoder.decode("invalid-token")).thenThrow(new JwtException("invalid target token"));
        OAuth2IntrospectionEndpoint endpoint = endpoint(decoder, clientPort);

        ResponseEntity<Map<String, Object>> response = endpoint.introspect("invalid-token", basic("client:secret"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Boolean.FALSE, response.getBody().get("active"));
        assertEquals(1, response.getBody().size());
    }

    private static void assertError(ResponseEntity<Map<String, Object>> response, HttpStatus status, String code) {
        assertEquals(status, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertTrue(response.getBody().containsKey("error_description"));
        assertEquals(code, response.getBody().get("error"));
    }

    private static OAuth2IntrospectionEndpoint endpoint(JwtDecoder decoder, OAuth2ClientDataPort clientPort) {
        return new OAuth2IntrospectionEndpoint(decoder, clientPort);
    }

    private static OAuth2ClientView client() {
        return new OAuth2ClientView("client", Set.of("read"), Set.of(), false);
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("jwt-token")
                .header("alg", "none")
                .subject("alice")
                .claim("client_id", "client")
                .claim("username", "alice")
                .claim("scope", "read write")
                .issuedAt(Instant.parse("2026-11-26T00:00:00Z"))
                .expiresAt(Instant.parse("2026-11-27T00:00:00Z"))
                .build();
    }

    private static String basic(String credentials) {
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
