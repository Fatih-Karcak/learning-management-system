package com.lms.apigateway.filter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class LearningRateLimitTest {

    @Test
    void studentsOnSameNetworkHaveSeparateRequestBudgets() throws Exception {
        var filter = new RateLimitFilter();
        String student = UUID.randomUUID().toString();
        for (int i = 0; i < 100; i++) assertEquals(200, request(filter, student));
        assertEquals(429, request(filter, student));
        assertEquals(200, request(filter, UUID.randomUUID().toString()));
        assertEquals(200, request(filter, null));
    }

    private int request(RateLimitFilter filter, String user) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/progress/lesson/playback");
        request.setRemoteAddr("192.0.2.1");
        if (user != null) request.addHeader("X-User-Id", user);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
