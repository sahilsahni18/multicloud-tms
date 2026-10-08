package com.trackflow.tms.security;

import com.trackflow.tms.config.AppProperties;
import com.trackflow.tms.entity.RoleName;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Issues and validates HS512-signed access tokens. */
@Service
public class JwtService {

    static final int MIN_KEY_BYTES = 64; // 512 bits for HS512
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLES = "roles";

    private final SecretKey key;
    private final String issuer;
    private final Duration ttl;
    private final Clock clock;
    private final JwtParser parser;

    public JwtService(AppProperties properties, Clock clock) {
        AppProperties.Jwt jwt = properties.security().jwt();
        byte[] keyBytes = Decoders.BASE64.decode(jwt.secret());
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be a Base64-encoded key of at least 512 bits (64 bytes)");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = jwt.issuer();
        this.ttl = jwt.accessTokenTtl();
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .clock(() -> Date.from(clock.instant()))
                .clockSkewSeconds(30)
                .build();
    }

    public IssuedToken issue(AuthUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_NAME, user.getFullName())
                .claim(CLAIM_ROLES, user.getRoles().stream().map(Enum::name).sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
        return new IssuedToken(token, expiresAt, ttl.toSeconds());
    }

    /**
     * @throws JwtException if the token is malformed, tampered with, expired or from another issuer
     */
    public AuthUser parse(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        try {
            Long id = Long.valueOf(claims.getSubject());
            Set<RoleName> roles = EnumSet.noneOf(RoleName.class);
            List<?> rawRoles = claims.get(CLAIM_ROLES, List.class);
            if (rawRoles != null) {
                rawRoles.forEach(role -> roles.add(RoleName.valueOf(String.valueOf(role))));
            }
            return AuthUser.fromToken(id, claims.get(CLAIM_EMAIL, String.class), claims.get(CLAIM_NAME, String.class), roles);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new MalformedJwtException("Token claims are invalid", e);
        }
    }

    public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) {
    }
}
