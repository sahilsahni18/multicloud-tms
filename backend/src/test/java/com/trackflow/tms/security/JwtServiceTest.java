package com.trackflow.tms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trackflow.tms.config.AppProperties;
import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.support.MutableClock;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET =
            "kQx/UfPHjmtvTQh78vwq6TWD1SEeBugBZ/Tq5xwpISLzFRYSXO8xuuAE4eBzqMB8QBOSjArSxgYuakakwCuWjA==";
    private static final String OTHER_SECRET =
            "+w2sC+J0eNRQL4fuiDI7K0w8c1Wf2UZA/CqfNaSOK2uJBaJSyYYU0Fzsyxa09cPfYifkHUlySg0mLm13OOiHuw==";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-08T10:00:00Z"));
    private final AuthUser pm = AuthUser.fromToken(2L, "pm@trackflow.dev", "Morgan Manager",
            Set.of(RoleName.PROJECT_MANAGER, RoleName.DEVELOPER));

    private static AppProperties properties(String secret, String issuer) {
        return new AppProperties(
                new AppProperties.Security(
                        new AppProperties.Jwt(secret, issuer, Duration.ofMinutes(15), Duration.ofDays(7)),
                        new AppProperties.RefreshCookie("tf_refresh", false, "Strict", "/api/v1/auth"),
                        new AppProperties.RateLimit(false, 10)),
                new AppProperties.Cors(List.of()),
                null);
    }

    private JwtService service(String secret, String issuer) {
        return new JwtService(properties(secret, issuer), clock);
    }

    @Test
    void issuedTokenRoundTripsIdentityAndRoles() {
        JwtService jwt = service(SECRET, "trackflow");
        JwtService.IssuedToken token = jwt.issue(pm);

        assertThat(token.expiresInSeconds()).isEqualTo(900);
        assertThat(token.expiresAt()).isEqualTo(Instant.parse("2026-10-08T10:15:00Z"));

        AuthUser parsed = jwt.parse(token.value());
        assertThat(parsed.getId()).isEqualTo(2L);
        assertThat(parsed.getEmail()).isEqualTo("pm@trackflow.dev");
        assertThat(parsed.getFullName()).isEqualTo("Morgan Manager");
        assertThat(parsed.getRoles()).containsExactlyInAnyOrder(RoleName.PROJECT_MANAGER, RoleName.DEVELOPER);
        assertThat(parsed.getAuthorities()).extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_PROJECT_MANAGER", "ROLE_DEVELOPER");
        assertThat(parsed.getPassword()).isNull();
    }

    @Test
    void tokenIsRejectedAfterExpiryPlusSkew() {
        JwtService jwt = service(SECRET, "trackflow");
        String token = jwt.issue(pm).value();

        clock.advance(Duration.ofMinutes(15).plusSeconds(20)); // inside 30 s skew
        assertThat(jwt.parse(token).getId()).isEqualTo(2L);

        clock.advance(Duration.ofSeconds(20));
        assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = service(OTHER_SECRET, "trackflow").issue(pm).value();
        assertThatThrownBy(() -> service(SECRET, "trackflow").parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String foreign = service(SECRET, "someone-else").issue(pm).value();
        assertThatThrownBy(() -> service(SECRET, "trackflow").parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void garbageIsRejected() {
        JwtService jwt = service(SECRET, "trackflow");
        assertThatThrownBy(() -> jwt.parse("not.a.jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void keyShorterThan512BitsIsRefusedAtStartup() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[32]);
        assertThatThrownBy(() -> service(shortKey, "trackflow"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("512 bits");
    }
}
