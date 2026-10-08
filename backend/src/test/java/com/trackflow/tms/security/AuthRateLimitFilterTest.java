package com.trackflow.tms.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.tms.support.MutableClock;
import com.trackflow.tms.util.IpRateLimiter;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(
            new IpRateLimiter(2, Duration.ofMinutes(1), new MutableClock(Instant.parse("2026-10-08T10:00:00Z"))),
            new SecurityProblemHandler(new ObjectMapper()));

    private MockHttpServletResponse call(String method, String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void loginIsLimitedPerIp() throws Exception {
        assertThat(call("POST", "/api/v1/auth/login", "1.2.3.4").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/v1/auth/login", "1.2.3.4").getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = call("POST", "/api/v1/auth/login", "1.2.3.4");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("30"); // 2 per minute: one back every 30 s
        assertThat(blocked.getContentType()).isEqualTo("application/problem+json");
        assertThat(blocked.getContentAsString()).contains("Too many requests");

        assertThat(call("POST", "/api/v1/auth/login", "5.6.7.8").getStatus()).isEqualTo(200);
    }

    @Test
    void otherEndpointsAreNotLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(call("GET", "/api/v1/auth/me", "1.2.3.4").getStatus()).isEqualTo(200);
            assertThat(call("POST", "/api/v1/auth/logout", "1.2.3.4").getStatus()).isEqualTo(200);
        }
    }
}
