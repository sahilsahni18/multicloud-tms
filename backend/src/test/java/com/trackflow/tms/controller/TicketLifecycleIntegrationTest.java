package com.trackflow.tms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.tms.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The PRD demo script, end to end through the API, plus the edge cases around it. */
class TicketLifecycleIntegrationTest extends AbstractIntegrationTest {

    private long createTicket(String email, Map<String, Object> overrides) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of(
                "projectId", TMS, "title", "Checkout crashes on submit", "type", "BUG", "priority", "CRITICAL"));
        body.putAll(overrides);
        return body(postAs(email, body, "/api/v1/tickets").andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode move(String email, long id, String status, int expectedHttp) throws Exception {
        return body(postAs(email, Map.of("status", status), "/api/v1/tickets/{id}/transitions", id)
                .andExpect(status().is(expectedHttp)));
    }

    @Test
    void demoScriptFullLifecycle() throws Exception {
        // 1. USER raises a CRITICAL bug.
        JsonNode created = body(postAs(USER, Map.of("projectId", TMS, "title", "Payment page shows 500",
                "description", "Happens on every card payment", "type", "BUG", "priority", "CRITICAL"),
                "/api/v1/tickets").andExpect(status().isCreated()));
        long id = created.get("id").asLong();
        assertThat(created.get("key").asText()).matches("TMS-\\d+");
        assertThat(created.get("status").asText()).isEqualTo("OPEN");
        assertThat(created.get("reporter").get("id").asLong()).isEqualTo(USER_ID);
        assertThat(created.get("allowedTransitions")).isEmpty();
        assertThat(created.get("permissions").get("canEdit").asBoolean()).isTrue();  // reporter, while OPEN
        assertThat(created.get("permissions").get("canAssign").asBoolean()).isFalse();

        // 2. PM assigns it to the developer; only project members can be assigned.
        putAs(PM, Map.of("assigneeId", ADMIN_ID), "/api/v1/tickets/{id}/assignee", id)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("not a member of project TMS")));
        putAs(PM, Map.of("assigneeId", DEV_ID), "/api/v1/tickets/{id}/assignee", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.id").value(DEV_ID));

        // 3. DEVELOPER works it: illegal jump rejected with 409, legal moves accepted, comment added.
        JsonNode illegal = move(DEV, id, "IN_REVIEW", 409);
        assertThat(illegal.get("detail").asText()).contains("Cannot move ticket from OPEN to IN_REVIEW");
        JsonNode inProgress = move(DEV, id, "IN_PROGRESS", 200);
        assertThat(inProgress.get("allowedTransitions").toString()).contains("IN_REVIEW", "OPEN");
        JsonNode inReview = move(DEV, id, "IN_REVIEW", 200);
        assertThat(inReview.get("allowedTransitions").toString()).contains("IN_PROGRESS").doesNotContain("CLOSED");
        postAs(DEV, Map.of("body", "Fixed the null check, ready for review."), "/api/v1/tickets/{id}/comments", id)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.id").value(DEV_ID));
        move(DEV, id, "CLOSED", 403);

        // 4. PM closes it.
        JsonNode closed = move(PM, id, "CLOSED", 200);
        assertThat(closed.get("closedAt").asText()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT closed_at IS NOT NULL FROM tickets WHERE id = ?", Boolean.class, id))
                .isTrue();

        // 5. The reporter can no longer edit, but can reopen within 14 days.
        putAs(USER, Map.of("title", "x", "type", "BUG", "priority", "LOW", "version", closed.get("version").asLong()),
                "/api/v1/tickets/{id}", id).andExpect(status().isForbidden());
        JsonNode reopened = move(USER, id, "OPEN", 200);
        assertThat(reopened.has("closedAt")).isFalse();   // null fields are omitted from responses
        move(USER, id, "IN_PROGRESS", 403);

        // 6. History shows who changed what, when.
        JsonNode history = body(getAs(USER, "/api/v1/tickets/{id}/history", id).andExpect(status().isOk()));
        List<String> changes = new ArrayList<>();
        history.forEach(h -> changes.add(h.get("changeType").asText() + ":" + h.path("oldValue").asText("-") + "->"
                + h.path("newValue").asText("-") + " by " + h.get("changedBy").get("fullName").asText()));
        assertThat(changes).containsExactly(
                "CREATED:-->- by Casey User",
                "ASSIGNED:-->Sam Developer by Morgan Manager",
                "STATUS_CHANGED:OPEN->IN_PROGRESS by Sam Developer",
                "STATUS_CHANGED:IN_PROGRESS->IN_REVIEW by Sam Developer",
                "STATUS_CHANGED:IN_REVIEW->CLOSED by Morgan Manager",
                "STATUS_CHANGED:CLOSED->OPEN by Casey User");
        history.forEach(h -> assertThat(h.get("changedAt").asText()).isNotBlank());

        // 7. Timeline merges the comment with the changes, in time order.
        JsonNode timeline = body(getAs(PM, "/api/v1/tickets/{id}/timeline", id).andExpect(status().isOk()));
        assertThat(timeline).hasSize(7);
        assertThat(timeline.findValuesAsText("kind")).contains("COMMENT", "CHANGE");

        // 8. Activity feed recorded it.
        Integer activityRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE entity_type = 'TICKET' AND entity_id = ?", Integer.class, id);
        assertThat(activityRows).isEqualTo(6);
    }

    @Test
    void reporterCannotReopenAfter14Days() throws Exception {
        long id = createTicket(USER, Map.of());
        move(PM, id, "CLOSED", 200);
        jdbc.update("UPDATE tickets SET closed_at = UTC_TIMESTAMP(6) - INTERVAL 15 DAY WHERE id = ?", id);
        move(USER, id, "OPEN", 403);
        move(PM, id, "OPEN", 200);
    }

    @Test
    void sameStatusIsAConflict() throws Exception {
        long id = createTicket(PM, Map.of());
        JsonNode body = move(PM, id, "OPEN", 409);
        assertThat(body.get("detail").asText()).contains("already OPEN");
    }

    @Test
    void optimisticLockingRejectsStaleEdits() throws Exception {
        long id = createTicket(PM, Map.of());
        long version = body(getAs(PM, "/api/v1/tickets/{id}", id)).get("version").asLong();

        JsonNode saved = body(putAs(PM, Map.of("title", "First edit", "type", "BUG", "priority", "HIGH",
                "dueDate", "2030-01-31", "version", version), "/api/v1/tickets/{id}", id).andExpect(status().isOk()));
        assertThat(saved.get("version").asLong()).isGreaterThan(version);
        assertThat(saved.get("dueDate").asText()).isEqualTo("2030-01-31");

        putAs(PM, Map.of("title", "Stale edit", "type", "BUG", "priority", "LOW", "version", version),
                "/api/v1/tickets/{id}", id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("changed by someone else")));

        JsonNode history = body(getAs(PM, "/api/v1/tickets/{id}/history", id));
        assertThat(history.findValuesAsText("field")).contains("title", "priority", "dueDate");
    }

    @Test
    void assigneeCanEditAndUnassignWorks() throws Exception {
        long id = createTicket(PM, Map.of("assigneeId", DEV2_ID));
        JsonNode ticket = body(getAs(DEV2, "/api/v1/tickets/{id}", id));
        assertThat(ticket.get("permissions").get("canEdit").asBoolean()).isTrue();
        putAs(DEV2, Map.of("title", "Clarified title", "type", "TASK", "priority", "MEDIUM",
                "version", ticket.get("version").asLong()), "/api/v1/tickets/{id}", id).andExpect(status().isOk());

        putAs(PM, new HashMap<>(Map.of()), "/api/v1/tickets/{id}/assignee", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").doesNotExist());
    }

    @Test
    void onlyManagersMayAssignAtCreation() throws Exception {
        postAs(USER, Map.of("projectId", TMS, "title", "Please assign", "assigneeId", DEV_ID), "/api/v1/tickets")
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotCreateTicketsInProjectsYouAreNotIn() throws Exception {
        postAs(USER, Map.of("projectId", OPS, "title", "Sneaky"), "/api/v1/tickets").andExpect(status().isNotFound());
    }

    @Test
    void ticketNumbersAreSequentialPerProject() throws Exception {
        long first = createTicket(PM, Map.of());
        long second = createTicket(PM, Map.of());
        int n1 = Integer.parseInt(body(getAs(PM, "/api/v1/tickets/{id}", first)).get("key").asText().substring(4));
        int n2 = Integer.parseInt(body(getAs(PM, "/api/v1/tickets/{id}", second)).get("key").asText().substring(4));
        assertThat(n2).isEqualTo(n1 + 1);
    }

    @Test
    void commentsCanBeEditedOnlyByAuthorOrAdmin() throws Exception {
        long id = createTicket(USER, Map.of());
        long commentId = body(postAs(USER, Map.of("body", "First!"), "/api/v1/tickets/{id}/comments", id)
                .andExpect(status().isCreated())).get("id").asLong();

        putAs(USER, Map.of("body", "First, edited"), "/api/v1/comments/{id}", commentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editedAt").exists())
                .andExpect(jsonPath("$.canModify").value(true));
        putAs(PM, Map.of("body", "Hijack"), "/api/v1/comments/{id}", commentId).andExpect(status().isForbidden());
        deleteAs(DEV, "/api/v1/comments/{id}", commentId).andExpect(status().isForbidden());
        deleteAs(ADMIN, "/api/v1/comments/{id}", commentId).andExpect(status().isNoContent());

        assertThat(body(getAs(USER, "/api/v1/tickets/{id}/comments", id))).isEmpty();
        putAs(USER, Map.of("body", "Back?"), "/api/v1/comments/{id}", commentId).andExpect(status().isNotFound());
        postAs(USER, Map.of("body", " "), "/api/v1/tickets/{id}/comments", id).andExpect(status().isBadRequest());
    }

    @Test
    void deletedTicketsDisappear() throws Exception {
        long id = createTicket(PM, Map.of());
        deleteAs(PM, "/api/v1/tickets/{id}", id).andExpect(status().isNoContent());
        getAs(PM, "/api/v1/tickets/{id}", id).andExpect(status().isNotFound());
        getAs(ADMIN, "/api/v1/tickets/{id}", id).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM tickets WHERE id = ?", Boolean.class, id))
                .isTrue();
    }

    @Test
    void validationErrorsAreReported() throws Exception {
        postAs(PM, Map.of("title", ""), "/api/v1/tickets")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.hasItems("projectId", "title")));
        postAs(PM, Map.of("projectId", TMS, "title", "x", "priority", "URGENT"), "/api/v1/tickets")
                .andExpect(status().isBadRequest());
    }
}
