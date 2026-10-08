package com.trackflow.tms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trackflow.tms.AbstractIntegrationTest;
import com.trackflow.tms.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppProperties properties;

    private MvcResult register(String email, String password, String fullName) throws Exception {
        return mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password, "fullName", fullName))))
                .andReturn();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    @Nested
    class Login {

        @Test
        void returnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
            MvcResult result = mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "admin@trackflow.dev", "password", DEMO_PASSWORD))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900))
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.email").value("admin@trackflow.dev"))
                    .andExpect(jsonPath("$.user.roles").value(contains("ADMIN")))
                    .andReturn();

            Cookie cookie = refreshCookie(result);
            assertThat(cookie).isNotNull();
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
            assertThat(cookie.getMaxAge()).isEqualTo(7 * 24 * 3600);
            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Strict");
            assertThat(result.getResponse().getContentAsString()).doesNotContain(cookie.getValue());
        }

        @Test
        void eachDemoRoleCanSignIn() throws Exception {
            Map<String, String> expected = Map.of(
                    "admin@trackflow.dev", "ADMIN",
                    "pm@trackflow.dev", "PROJECT_MANAGER",
                    "dev@trackflow.dev", "DEVELOPER",
                    "user@trackflow.dev", "USER");
            for (var entry : expected.entrySet()) {
                String token = accessTokenFor(entry.getKey());
                mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.email").value(entry.getKey()))
                        .andExpect(jsonPath("$.roles").value(contains(entry.getValue())));
            }
        }

        @Test
        void emailIsCaseInsensitive() throws Exception {
            login("PM@TrackFlow.DEV", DEMO_PASSWORD);
        }

        @Test
        void wrongPasswordReturns401Problem() throws Exception {
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "admin@trackflow.dev", "password", "wrong-password"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Unauthorized"))
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                    .andExpect(jsonPath("$.instance").value("/api/v1/auth/login"))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }

        @Test
        void unknownEmailGetsTheSameAnswerAsWrongPassword() throws Exception {
            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "nobody@trackflow.dev", "password", DEMO_PASSWORD))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        }

        @Test
        void disabledUserCannotSignIn() throws Exception {
            String email = uniqueEmail();
            assertThat(register(email, DEMO_PASSWORD, "Soon Disabled").getResponse().getStatus()).isEqualTo(201);
            jdbc.update("UPDATE users SET enabled = FALSE WHERE email = ?", email);

            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", email, "password", DEMO_PASSWORD))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        }

        @Test
        void recordsLastLogin() throws Exception {
            login("dev2@trackflow.dev", DEMO_PASSWORD);
            Long secondsAgo = jdbc.queryForObject(
                    "SELECT TIMESTAMPDIFF(SECOND, last_login_at, UTC_TIMESTAMP()) FROM users WHERE email = ?",
                    Long.class, "dev2@trackflow.dev");
            assertThat(secondsAgo).isBetween(0L, 60L);
        }
    }

    @Nested
    class Register {

        @Test
        void createsUserWithUserRoleAndSignsIn() throws Exception {
            String email = uniqueEmail();
            MvcResult result = register(email.toUpperCase(), DEMO_PASSWORD, "  New Person ");

            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            assertThat(refreshCookie(result)).isNotNull();
            mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken(result))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.fullName").value("New Person"))
                    .andExpect(jsonPath("$.roles").value(contains("USER")));

            String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
            assertThat(hash).startsWith("$2a$12$").doesNotContain(DEMO_PASSWORD);
        }

        @Test
        void duplicateEmailReturns409() throws Exception {
            MvcResult result = register("PM@trackflow.dev", DEMO_PASSWORD, "Copy Cat");
            assertThat(result.getResponse().getStatus()).isEqualTo(409);
            assertThat(result.getResponse().getContentAsString()).contains("already exists");
        }

        @Test
        void invalidInputReturns400WithFieldErrors() throws Exception {
            mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "not-an-email", "password", "short", "fullName", ""))))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.errors[*].field", hasItems("email", "password", "fullName")));
        }

        @Test
        void malformedJsonReturns400Problem() throws Exception {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty());
        }
    }

    @Nested
    class AccessToken {

        @Test
        void protectedEndpointWithoutTokenReturns401() throws Exception {
            mvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource"));
        }

        @Test
        void tamperedTokenReturns401() throws Exception {
            String token = accessTokenFor("user@trackflow.dev");
            String[] parts = token.split("\\.");
            String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                            .replace("\"USER\"", "\"ADMIN\"").getBytes(StandardCharsets.UTF_8));
            String forged = parts[0] + "." + forgedPayload + "." + parts[2];

            mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Access token is invalid"));
        }

        @Test
        void expiredTokenReturns401WithReason() throws Exception {
            var key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.security().jwt().secret()));
            Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
            String expired = Jwts.builder()
                    .issuer("trackflow")
                    .subject("1")
                    .claim("email", "admin@trackflow.dev")
                    .claim("name", "Alex Admin")
                    .claim("roles", List.of("ADMIN"))
                    .issuedAt(Date.from(past.minus(15, ChronoUnit.MINUTES)))
                    .expiration(Date.from(past))
                    .signWith(key, Jwts.SIG.HS512)
                    .compact();

            mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Access token has expired"));
        }
    }

    @Nested
    class RefreshAndLogout {

        @Test
        void refreshRotatesTokenAndReuseRevokesTheFamily() throws Exception {
            Cookie first = refreshCookie(login("dev@trackflow.dev", DEMO_PASSWORD));

            MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.email").value("dev@trackflow.dev"))
                    .andReturn();
            Cookie second = refreshCookie(refreshed);
            assertThat(second.getValue()).isNotEqualTo(first.getValue());

            // Replaying the old token = theft signal: rejected, and the whole family is revoked.
            mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Refresh token has been revoked"));
            mvc.perform(post("/api/v1/auth/refresh").cookie(second))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
            Cookie cookie = refreshCookie(login("pm@trackflow.dev", DEMO_PASSWORD));

            MvcResult logout = mvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                    .andExpect(status().isNoContent())
                    .andReturn();
            assertThat(logout.getResponse().getCookie(REFRESH_COOKIE).getMaxAge()).isZero();

            mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void logoutWithoutCookieIsStillNoContent() throws Exception {
            mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
        }

        @Test
        void refreshWithoutCookieReturns401() throws Exception {
            mvc.perform(post("/api/v1/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Refresh token is missing"));
        }

        @Test
        void refreshWithUnknownTokenReturns401() throws Exception {
            mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, "made-up")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Refresh token is invalid"));
        }

        @Test
        void refreshIsRejectedOnceTheUserIsDisabled() throws Exception {
            String email = uniqueEmail();
            Cookie cookie = refreshCookie(register(email, DEMO_PASSWORD, "Disabled Later"));
            jdbc.update("UPDATE users SET enabled = FALSE WHERE email = ?", email);

            mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Account is disabled"));
        }

        @Test
        void refreshTokensAreStoredOnlyAsHashes() throws Exception {
            Cookie cookie = refreshCookie(login("user@trackflow.dev", DEMO_PASSWORD));
            Integer plain = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, cookie.getValue());
            assertThat(plain).isZero();
        }
    }

    @Nested
    class Platform {

        @Test
        void correlationIdIsEchoedOrGenerated() throws Exception {
            mvc.perform(get("/actuator/health").header("X-Correlation-Id", "demo-123"))
                    .andExpect(header().string("X-Correlation-Id", "demo-123"));
            mvc.perform(get("/actuator/health"))
                    .andExpect(header().string("X-Correlation-Id", matchesPattern("[0-9a-f-]{36}")));
        }

        @Test
        void healthProbesArePublic() throws Exception {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
            mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
            mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        }

        @Test
        void openApiDocumentListsAuthEndpoints() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/auth/refresh']").exists())
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        }

        @Test
        void corsPreflightAllowsTheFrontendOrigin() throws Exception {
            mvc.perform(options("/api/v1/auth/login")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
            mvc.perform(options("/api/v1/auth/login")
                            .header(HttpHeaders.ORIGIN, "https://evil.example")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden());
        }
    }
}
