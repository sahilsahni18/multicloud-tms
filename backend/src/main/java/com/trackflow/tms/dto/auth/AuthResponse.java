package com.trackflow.tms.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** Returned by register / login / refresh. The refresh token is only in the httpOnly cookie. */
public record AuthResponse(
        String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Access token lifetime in seconds", example = "900") long expiresIn,
        AuthUserResponse user) {

    public static AuthResponse bearer(String accessToken, long expiresIn, AuthUserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}
