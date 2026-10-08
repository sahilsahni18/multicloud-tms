package com.trackflow.tms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.tms.AbstractIntegrationTest;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

class ProjectIntegrationTest extends AbstractIntegrationTest {

    private static String uniqueKey() {
        StringBuilder key = new StringBuilder("Z");
        for (int i = 0; i < 6; i++) {
            key.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
        }
        return key.toString();
    }

    private JsonNode createProject(String email, String key) throws Exception {
        return body(postAs(email, Map.of("key", key, "name", "Project " + key, "description", "Test project"),
                "/api/v1/projects").andExpect(status().isCreated()));
    }

    @Test
    void managerCreatesAndRunsAProject() throws Exception {
        String key = uniqueKey();
        JsonNode project = body(postAs(PM, Map.of("key", key.toLowerCase(), "name", "Website"), "/api/v1/projects")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/projects/"))));
        long id = project.get("id").asLong();
        assertThat(project.get("key").asText()).isEqualTo(key);           // normalised to upper case
        assertThat(project.get("owner").get("id").asLong()).isEqualTo(PM_ID);
        assertThat(project.get("memberCount").asLong()).isEqualTo(1);
        assertThat(project.get("canManage").asBoolean()).isTrue();

        // Members: add dev, cannot remove the owner, can remove dev.
        JsonNode members = body(postAs(PM, Map.of("userId", DEV_ID), "/api/v1/projects/{id}/members", id)
                .andExpect(status().isOk()));
        assertThat(members.findValuesAsText("email")).containsExactly(PM, DEV);   // owner first
        deleteAs(PM, "/api/v1/projects/{id}/members/{u}", id, PM_ID).andExpect(status().isBadRequest());

        // The new member can now see the project and raise tickets in it; the key prefixes the ticket.
        getAs(DEV, "/api/v1/projects/{id}", id).andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").value(false));
        postAs(DEV, Map.of("projectId", id, "title", "First ticket"), "/api/v1/tickets")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(key + "-1"));
        getAs(PM, "/api/v1/projects/{id}", id).andExpect(jsonPath("$.totalTickets").value(1))
                .andExpect(jsonPath("$.openTickets").value(1))
                .andExpect(jsonPath("$.memberCount").value(2));

        deleteAs(PM, "/api/v1/projects/{id}/members/{u}", id, DEV_ID).andExpect(status().isOk());
        getAs(DEV, "/api/v1/projects/{id}", id).andExpect(status().isNotFound());

        // Update by the owner; a developer is stopped by the role check.
        putAs(PM, Map.of("name", "Website v2", "description", "Renamed"), "/api/v1/projects/{id}", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Website v2"));
    }

    @Test
    void projectKeysAreUniqueAndValidated() throws Exception {
        String key = uniqueKey();
        createProject(PM, key);
        postAs(ADMIN, Map.of("key", key, "name", "Duplicate"), "/api/v1/projects").andExpect(status().isConflict());
        postAs(PM, Map.of("key", "1AB", "name", "Bad key"), "/api/v1/projects").andExpect(status().isBadRequest());
        postAs(PM, Map.of("key", "A", "name", "Too short"), "/api/v1/projects").andExpect(status().isBadRequest());
    }

    @Test
    void adminCanCreateForAManagerButNotForADeveloper() throws Exception {
        postAs(ADMIN, Map.of("key", uniqueKey(), "name", "For PM", "ownerId", PM_ID), "/api/v1/projects")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner.id").value(PM_ID));
        postAs(ADMIN, Map.of("key", uniqueKey(), "name", "For dev", "ownerId", DEV_ID), "/api/v1/projects")
                .andExpect(status().isBadRequest());
        postAs(PM, Map.of("key", uniqueKey(), "name", "For admin", "ownerId", ADMIN_ID), "/api/v1/projects")
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDeleteHidesProjectAndItsTickets() throws Exception {
        String key = uniqueKey();
        long id = createProject(PM, key).get("id").asLong();
        long ticketId = body(postAs(PM, Map.of("projectId", id, "title", "Doomed"), "/api/v1/tickets")
                .andExpect(status().isCreated())).get("id").asLong();

        deleteAs(ADMIN, "/api/v1/projects/{id}", id).andExpect(status().isNoContent());

        getAs(PM, "/api/v1/projects/{id}", id).andExpect(status().isNotFound());
        getAs(PM, "/api/v1/tickets/{id}", ticketId).andExpect(status().isNotFound());
        JsonNode keys = body(getAs(ADMIN, "/api/v1/projects?size=100")).get("content");
        assertThat(keys.findValuesAsText("key")).doesNotContain(key);
    }

    @Test
    void searchProjectsByText() throws Exception {
        JsonNode page = body(getAs(ADMIN, "/api/v1/projects?q=Cloud"));
        assertThat(page.get("content").findValuesAsText("key")).containsExactly("OPS");
    }
}
