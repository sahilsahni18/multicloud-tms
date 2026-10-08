package com.trackflow.tms.controller;

import com.trackflow.tms.dto.auth.AuthResponse;
import com.trackflow.tms.dto.auth.AuthUserResponse;
import com.trackflow.tms.dto.auth.LoginRequest;
import com.trackflow.tms.dto.auth.RegisterRequest;
import com.trackflow.tms.exception.InvalidTokenException;
import com.trackflow.tms.security.AuthUser;
import com.trackflow.tms.security.RefreshCookieManager;
import com.trackflow.tms.service.AuthService;
import com.trackflow.tms.util.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token refresh and logout")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieManager refreshCookies;

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Create an account (role USER) and sign in")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest http) {
        return withRefreshCookie(HttpStatus.CREATED, authService.register(request, ClientInfo.from(http)));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Sign in; returns an access token and sets the refresh-token cookie")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withRefreshCookie(HttpStatus.OK, authService.login(request, ClientInfo.from(http)));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Exchange the refresh-token cookie for a new access token (rotates the cookie)")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest http) {
        String refreshToken = refreshCookies.read(http)
                .orElseThrow(() -> new InvalidTokenException("Refresh token is missing"));
        return withRefreshCookie(HttpStatus.OK, authService.refresh(refreshToken, ClientInfo.from(http)));
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Revoke the refresh token and clear the cookie")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        refreshCookies.read(http).ifPresent(authService::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user and roles")
    public AuthUserResponse me(@AuthenticationPrincipal AuthUser user) {
        return authService.currentUser(user);
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthService.AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookies.create(result.refreshToken(), result.refreshTokenExpiresAt()).toString())
                .body(result.body());
    }
}
