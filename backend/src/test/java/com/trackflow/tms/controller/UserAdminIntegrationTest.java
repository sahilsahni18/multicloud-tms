package com.trackflow.tms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.tms.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class UserAdminIntegrationTest extends AbstractIntegrationTest {

    private static String uniqueEmail() {
        return "u-" + UUID.randomUUID() + "@example.test";
    }

    private JsonNode createUser(String email, List<String> roles) throws Exception {
        return body(postAs(ADMIN, Map.of("email", email, "fullName", "Test Person", "password", DEMO_PASSWORD,
                "roles", roles), "/api/v1/users").andExpect(status().isCreated()));
    }

    private int loginStatus(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password)))).andReturn().getResponse().getStatus();
    }

    @Test
    void adminManagesAUserFromCreationToDeletion() throws Exception {
        String email = uniqueEmail();
        JsonNode created = createUser(email, List.of("DEVELOPER"));
        long id = created.get("id").asLong();
        assertThat(created.get("roles").toString()).isEqualTo("[\"DEVELOPER\"]");
        assertThat(created.get("enabled").asBoolean()).isTrue();

        postAs(ADMIN, Map.of("email", email, "fullName", "Again", "password", DEMO_PASSWORD, "roles", List.of("USER")),
                "/api/v1/users").andExpect(status().isConflict());

        // Promote: a fresh login carries the new roles.
        putAs(ADMIN, Map.of("roles", List.of("PROJECT_MANAGER", "DEVELOPER")), "/api/v1/users/{id}/roles", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(contains("PROJECT_MANAGER", "DEVELOPER")));
        String token = accessToken(login(email, DEMO_PASSWORD));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.roles").value(contains("PROJECT_MANAGER", "DEVELOPER")));

        // Disable: login refused and existing refresh tokens revoked.
        Cookie refresh = refreshCookie(login(email, DEMO_PASSWORD));
        putAs(ADMIN, Map.of("email", email, "fullName", "Test Person", "enabled", false), "/api/v1/users/{id}", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        assertThat(loginStatus(email, DEMO_PASSWORD)).isEqualTo(401);
        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());

        // Delete: gone from lists and lookups.
        deleteAs(ADMIN, "/api/v1/users/{id}", id).andExpect(status().isNoContent());
        getAs(ADMIN, "/api/v1/users/{id}", id).andExpect(status().isNotFound());
        assertThat(body(getAs(ADMIN, "/api/v1/users?q={q}", email)).get("totalElements").asLong()).isZero();
    }

    @Test
    void adminResetsAForgottenPassword() throws Exception {
        String email = uniqueEmail();
        long id = createUser(email, List.of("DEVELOPER")).get("id").asLong();
        Cookie session = refreshCookie(login(email, DEMO_PASSWORD));

        putAs(ADMIN, Map.of("newPassword", "short"), "/api/v1/users/{id}/password", id)
                .andExpect(status().isBadRequest());
        putAs(ADMIN, Map.of("newPassword", "Temporary@2026"), "/api/v1/users/{id}/password", id)
                .andExpect(status().isNoContent());

        assertThat(loginStatus(email, DEMO_PASSWORD)).isEqualTo(401);
        assertThat(loginStatus(email, "Temporary@2026")).isEqualTo(200);
        mvc.perform(post("/api/v1/auth/refresh").cookie(session)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE action = 'USER_PASSWORD_RESET' AND entity_id = ?",
                Integer.class, id)).isEqualTo(1);

        // Admins use Profile (which checks the current password) for their own.
        putAs(ADMIN, Map.of("newPassword", "Temporary@2026"), "/api/v1/users/{id}/password", ADMIN_ID)
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCannotLockThemselvesOut() throws Exception {
        deleteAs(ADMIN, "/api/v1/users/{id}", ADMIN_ID).andExpect(status().isBadRequest());
        putAs(ADMIN, Map.of("roles", List.of("USER")), "/api/v1/users/{id}/roles", ADMIN_ID)
                .andExpect(status().isBadRequest());
        putAs(ADMIN, Map.of("email", ADMIN, "fullName", "Alex Admin", "enabled", false), "/api/v1/users/{id}", ADMIN_ID)
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFiltersByRoleAndText() throws Exception {
        JsonNode devs = body(getAs(ADMIN, "/api/v1/users?role=DEVELOPER&size=100").andExpect(status().isOk()));
        assertThat(devs.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);
        devs.get("content").forEach(u -> assertThat(u.get("roles").toString()).contains("DEVELOPER"));

        JsonNode casey = body(getAs(ADMIN, "/api/v1/users?q=Casey"));
        assertThat(casey.get("content").findValuesAsText("email")).containsExactly(USER);

        JsonNode lookup = body(getAs(PM, "/api/v1/users/lookup?q=Developer"));
        assertThat(lookup.findValuesAsText("email")).contains(DEV, DEV2);

        JsonNode roles = body(getAs(ADMIN, "/api/v1/roles"));
        assertThat(roles.findValuesAsText("name")).containsExactly("ADMIN", "PROJECT_MANAGER", "DEVELOPER", "USER");
    }

    @Test
    void usersManageTheirOwnProfileAndPassword() throws Exception {
        String email = uniqueEmail();
        createUser(email, List.of("USER"));
        MvcResult session = login(email, DEMO_PASSWORD);
        String token = accessToken(session);
        Cookie refresh = refreshCookie(session);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("fullName", "Renamed Person"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed Person"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "wrong-password", "newPassword", "NewPass@456"))))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", DEMO_PASSWORD, "newPassword", "NewPass@456"))))
                .andExpect(status().isNoContent());

        assertThat(loginStatus(email, DEMO_PASSWORD)).isEqualTo(401);
        assertThat(loginStatus(email, "NewPass@456")).isEqualTo(200);
        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }
}
