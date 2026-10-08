package com.trackflow.tms.service;

import com.trackflow.tms.config.AppProperties;
import com.trackflow.tms.entity.RefreshToken;
import com.trackflow.tms.entity.User;
import com.trackflow.tms.exception.InvalidTokenException;
import com.trackflow.tms.repository.RefreshTokenRepository;
import com.trackflow.tms.security.AuthUser;
import com.trackflow.tms.util.ClientInfo;
import com.trackflow.tms.util.TokenHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token rotation with reuse detection:
 * every refresh revokes the presented token and issues a new one in the same
 * family; presenting an already-revoked token revokes the whole family, which
 * logs out both the attacker and the victim.
 */
@Slf4j
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final Duration ttl;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.ttl = properties.security().jwt().refreshTokenTtl();
        this.clock = clock;
    }

    /** Starts a new token family (login / register). */
    @Transactional
    public IssuedRefreshToken issueNewFamily(User user, ClientInfo client) {
        return issue(user, UUID.randomUUID().toString(), client);
    }

    /**
     * Validates and rotates a refresh token. Revocations made while rejecting
     * a token must survive the exception, hence noRollbackFor.
     */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public Rotation rotate(String rawToken, ClientInfo client) {
        RefreshToken current = repository.findByTokenHashForUpdate(TokenHasher.sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid"));
        Instant now = clock.instant();

        if (current.getRevokedAt() != null) {
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected: user={} family={} revokedTokens={}",
                    current.getUser().getId(), current.getFamilyId(), revoked);
            throw new InvalidTokenException("Refresh token has been revoked");
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw new InvalidTokenException("Refresh token has expired");
        }
        User user = current.getUser();
        if (!user.isActive()) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidTokenException("Account is disabled");
        }

        IssuedRefreshToken next = issue(user, current.getFamilyId(), client);
        current.setRevokedAt(now);
        current.setReplacedById(next.id());
        return new Rotation(AuthUser.fromEntity(user), next);
    }

    /** Logout. Unknown or already-revoked tokens are ignored (idempotent). */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(clock.instant()));
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    private IssuedRefreshToken issue(User user, String familyId, ClientInfo client) {
        String raw = TokenHasher.newToken();
        Instant now = clock.instant();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(TokenHasher.sha256Hex(raw));
        token.setFamilyId(familyId);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(ttl));
        token.setCreatedIp(client.ip());
        token.setUserAgent(client.userAgent());
        repository.save(token);
        return new IssuedRefreshToken(token.getId(), raw, token.getExpiresAt());
    }

    public record IssuedRefreshToken(Long id, String rawToken, Instant expiresAt) {
    }

    public record Rotation(AuthUser user, IssuedRefreshToken refreshToken) {
    }
}
