package com.trackflow.tms.security;

import com.trackflow.tms.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * The refresh token travels only in an httpOnly cookie scoped to /api/v1/auth,
 * so page JavaScript can never read it and it is not sent to other endpoints.
 */
@Component
public class RefreshCookieManager {

    private final AppProperties.RefreshCookie settings;
    private final Clock clock;

    public RefreshCookieManager(AppProperties properties, Clock clock) {
        this.settings = properties.security().refreshCookie();
        this.clock = clock;
    }

    public ResponseCookie create(String rawToken, Instant expiresAt) {
        long millis = Duration.between(clock.instant(), expiresAt).toMillis();
        long seconds = Math.max(0, (millis + 999) / 1000); // round up: 6d23h59m59.9s -> 7d
        return base(rawToken).maxAge(seconds).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, settings.name());
        return Optional.ofNullable(cookie).map(Cookie::getValue).filter(value -> !value.isBlank());
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(settings.name(), value)
                .httpOnly(true)
                .secure(settings.secure())
                .sameSite(settings.sameSite())
                .path(settings.path());
    }
}
