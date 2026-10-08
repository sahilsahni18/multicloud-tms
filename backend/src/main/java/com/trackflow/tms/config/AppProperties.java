package com.trackflow.tms.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed view of the {@code app.*} configuration. Bound and validated at
 * start-up, so a missing JWT secret stops the application immediately.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Security security,
        @Valid @NotNull Cors cors,
        Bootstrap bootstrap) {

    public record Security(
            @Valid @NotNull Jwt jwt,
            @Valid @NotNull RefreshCookie refreshCookie,
            @Valid @NotNull RateLimit authRateLimit) {
    }

    /**
     * @param secret Base64-encoded HMAC key, at least 512 bits (HS512)
     */
    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {
    }

    public record RefreshCookie(
            @NotBlank String name,
            boolean secure,
            @NotBlank String sameSite,
            @NotBlank String path) {
    }

    public record RateLimit(boolean enabled, @Min(1) int requestsPerMinute) {
    }

    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    /**
     * Optional first admin for environments without demo data (cloud).
     * Created on start-up only if no user with that email exists.
     */
    public record Bootstrap(String adminEmail, String adminPassword, String adminName) {
    }
}
