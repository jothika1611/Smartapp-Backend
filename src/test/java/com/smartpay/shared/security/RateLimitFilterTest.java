package com.smartpay.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpay.shared.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private RateLimitFilter filter(int authLimit) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setAuthPerMinute(authLimit);
        properties.setPerMinute(100);
        return new RateLimitFilter(properties, new ObjectMapper());
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("203.0.113.10");
        return request;
    }

    @Test
    void authEndpointsReturn429AfterTheConfiguredLimit() throws Exception {
        RateLimitFilter filter = filter(2);

        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/auth/login"), first, new MockFilterChain());
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/auth/login"), second, new MockFilterChain());
        MockHttpServletResponse third = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/auth/login"), third, new MockFilterChain());

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(third.getStatus()).isEqualTo(429);
        assertThat(third.getContentAsString()).contains("Too many requests");
    }

    @Test
    void nonApiPathsAreNeverRateLimited() throws Exception {
        RateLimitFilter filter = filter(0);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/actuator/health"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
