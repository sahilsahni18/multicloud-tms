package com.trackflow.tms.service;

import com.trackflow.tms.dto.auth.AuthResponse;
import com.trackflow.tms.dto.auth.AuthUserResponse;
import com.trackflow.tms.dto.auth.LoginRequest;
import com.trackflow.tms.dto.auth.RegisterRequest;
import com.trackflow.tms.entity.Role;
import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.entity.User;
import com.trackflow.tms.exception.ConflictException;
import com.trackflow.tms.exception.NotFoundException;
import com.trackflow.tms.mapper.UserMapper;
import com.trackflow.tms.repository.RoleRepository;
import com.trackflow.tms.repository.UserRepository;
import com.trackflow.tms.security.AuthUser;
import com.trackflow.tms.security.JwtService;
import com.trackflow.tms.util.ClientInfo;
import com.trackflow.tms.util.EmailUtils;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    /** Self-service sign-up. New accounts always get the USER role. */
    @Transactional
    public AuthResult register(RegisterRequest request, ClientInfo client) {
        String email = EmailUtils.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("Role USER is missing; check migrations"));

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.getRoles().add(userRole);
        userRepository.save(user);
        log.info("User registered: id={}", user.getId());

        return issueTokens(AuthUser.fromEntity(user), refreshTokenService.issueNewFamily(user, client));
    }

    @Transactional
    public AuthResult login(LoginRequest request, ClientInfo client) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        EmailUtils.normalize(request.email()), request.password()));
        AuthUser principal = (AuthUser) authentication.getPrincipal();

        userRepository.updateLastLoginAt(principal.getId(), Instant.now(clock));
        User user = userRepository.getReferenceById(principal.getId());
        return issueTokens(principal, refreshTokenService.issueNewFamily(user, client));
    }

    public AuthResult refresh(String rawRefreshToken, ClientInfo client) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken, client);
        return issueTokens(rotation.user(), rotation.refreshToken());
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    /** Fresh from the database, so profile changes show without a new login. */
    @Transactional(readOnly = true)
    public AuthUserResponse currentUser(AuthUser principal) {
        return userRepository.findByIdAndDeletedAtIsNull(principal.getId())
                .map(userMapper::toAuthUser)
                .orElseThrow(() -> NotFoundException.of("User", principal.getId()));
    }

    private AuthResult issueTokens(AuthUser user, RefreshTokenService.IssuedRefreshToken refreshToken) {
        JwtService.IssuedToken accessToken = jwtService.issue(user);
        AuthResponse body = AuthResponse.bearer(accessToken.value(), accessToken.expiresInSeconds(),
                userMapper.toAuthUser(user));
        return new AuthResult(body, refreshToken.rawToken(), refreshToken.expiresAt());
    }

    /** What the controller needs: the JSON body plus the refresh token for the cookie. */
    public record AuthResult(AuthResponse body, String refreshToken, Instant refreshTokenExpiresAt) {
    }
}
