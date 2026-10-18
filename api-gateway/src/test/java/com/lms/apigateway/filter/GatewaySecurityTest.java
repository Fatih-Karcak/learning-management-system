package com.lms.apigateway.filter;

import com.lms.apigateway.security.JwtTokenProvider;
import com.lms.apigateway.security.UserAccountClient;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GatewaySecurityTest {
    private final JwtTokenProvider tokens = mock(JwtTokenProvider.class);
    private final UserAccountClient accounts = mock(UserAccountClient.class);
    private final GatewayAuthFilter filter = new GatewayAuthFilter(tokens, accounts);

    private void validToken(UUID id) {
        when(tokens.validateToken("valid")).thenReturn(true);
        when(tokens.getUserIdFromToken("valid")).thenReturn(id);
        when(tokens.getRoleFromToken("valid")).thenReturn("STUDENT");
        when(tokens.getNameFromToken("valid")).thenReturn("Test Student");
        when(accounts.isActive(id, "STUDENT")).thenReturn(true);
    }

    @Test void replacesIdentityHeadersRegardlessOfCase() throws Exception {
        UUID id = UUID.randomUUID(); validToken(id);
        for (String header : new String[]{"X-User-Id", "x-user-id", "X-uSeR-iD"}) {
            var request = new MockHttpServletRequest("GET", "/api/v1/payments/my");
            request.addHeader("Authorization", "Bearer valid");
            request.addHeader(header, UUID.randomUUID().toString());
            var chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            var forwarded = (HttpServletRequest) chain.getRequest();
            assertEquals(id.toString(), forwarded.getHeader(header));
            assertEquals(java.util.List.of(id.toString()), Collections.list(forwarded.getHeaders(header)));
            assertEquals(1, Collections.list(forwarded.getHeaderNames()).stream()
                    .filter(name -> name.equalsIgnoreCase("X-User-Id")).count());
        }
    }

    @Test void rejectsMissingOrInvalidTokensBeforeForwarding() throws Exception {
        for (String token : new String[]{null, "Bearer refresh"}) {
            var request = new MockHttpServletRequest("GET", "/api/v1/payments/my");
            if (token != null) request.addHeader("Authorization", token);
            var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertEquals(401, response.getStatus()); assertNull(chain.getRequest());
        }
    }

    @Test void stripsAnonymousSpoofedIdentity() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/courses");
        request.addHeader("x-user-id", "forged"); request.addHeader("x-user-role", "ADMIN");
        var chain = new MockFilterChain(); filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertNull(((HttpServletRequest)chain.getRequest()).getHeader("X-User-Id"));
        assertNull(((HttpServletRequest)chain.getRequest()).getHeader("X-User-Role"));
    }

    @Test void rejectsDeactivatedAccountAcrossServices() throws Exception {
        UUID id = UUID.randomUUID(); validToken(id);
        when(accounts.isActive(id, "STUDENT")).thenReturn(false);
        var request = new MockHttpServletRequest("GET", "/api/v1/enrollments/my");
        request.addHeader("Authorization", "Bearer valid");
        var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus()); assertNull(chain.getRequest());
    }

    @Test void failsClosedWhenAccountServiceIsUnavailable() throws Exception {
        UUID id = UUID.randomUUID(); validToken(id);
        when(accounts.isActive(id, "STUDENT")).thenThrow(new RestClientException("unavailable"));
        var request = new MockHttpServletRequest("GET", "/api/v1/payments/my");
        request.addHeader("Authorization", "Bearer valid");
        var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(503, response.getStatus()); assertNull(chain.getRequest());
    }

    @Test void doesNotTreatSimilarPathAsPublic() throws Exception {
        var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/courses-private"), response, chain);
        assertEquals(401, response.getStatus()); assertNull(chain.getRequest());
    }

    @Test void certificateVerificationRemainsPublic() throws Exception {
        var chain = new MockFilterChain(); var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/enrollments/certificates/verify/code"), response, chain);
        assertEquals(200, response.getStatus()); assertNotNull(chain.getRequest());
    }

    @Test void rateLimitResponseIsUtf8Json() throws Exception {
        var limiter = new RateLimitFilter(); var response = new MockHttpServletResponse();
        for (int i = 0; i < 100; i++) {
            response = new MockHttpServletResponse();
            limiter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
        response = new MockHttpServletResponse();
        limiter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), response, new MockFilterChain());
        assertEquals(429, response.getStatus());
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertTrue(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8).contains("Çok fazla istek"));
    }
}
