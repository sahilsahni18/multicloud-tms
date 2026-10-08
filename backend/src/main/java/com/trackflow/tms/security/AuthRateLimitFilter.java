package com.trackflow.tms.security;

import com.trackflow.tms.util.IpRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Brute-force protection for the credential endpoints: N requests per minute
 * per client IP. Counters are per pod; with 2 replicas the effective limit is
 * 2N, which is acceptable for this tier (a shared Redis bucket is the upgrade).
 */
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh");

    private final IpRateLimiter limiter;
    private final SecurityProblemHandler problems;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        IpRateLimiter.Decision decision = limiter.tryAcquire(request.getRemoteAddr());
        if (!decision.allowed()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
            problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
                    "Too many authentication attempts. Try again in " + decision.retryAfterSeconds() + " seconds.");
            return;
        }
        chain.doFilter(request, response);
    }
}
