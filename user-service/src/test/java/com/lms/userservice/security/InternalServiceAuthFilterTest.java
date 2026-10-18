package com.lms.userservice.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

class InternalServiceAuthFilterTest {
    private static final String KEY = "LocalServiceRegressionKeyAtLeast32Characters";
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void rejectsMissingAndIncorrectCredentials() throws Exception {
        for (String supplied : new String[]{null, "incorrect"}) {
            var request = new MockHttpServletRequest("GET", "/api/v1/internal/users/test");
            if (supplied != null) request.addHeader("X-Internal-Service-Key", supplied);
            var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
            new InternalServiceAuthFilter(KEY).doFilter(request, response, chain);
            assertEquals(401, response.getStatus()); assertNull(chain.getRequest());
        }
    }

    @Test void acceptsServiceCredentialAndSetsServiceAuthority() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/internal/users/test");
        request.addHeader("X-Internal-Service-Key", KEY); var chain = new MockFilterChain();
        new InternalServiceAuthFilter(KEY).doFilter(request, new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SERVICE")));
    }
}
