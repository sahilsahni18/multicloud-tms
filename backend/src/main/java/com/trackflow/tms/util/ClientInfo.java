package com.trackflow.tms.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * Who is calling, recorded with each refresh token. The remote address is
 * already the real client IP: server.forward-headers-strategy=native trusts
 * X-Forwarded-For only from internal (ingress / load balancer) addresses.
 */
public record ClientInfo(String ip, String userAgent) {

    private static final int MAX_USER_AGENT = 255;

    public static ClientInfo from(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT);
        }
        return new ClientInfo(request.getRemoteAddr(), userAgent);
    }
}
