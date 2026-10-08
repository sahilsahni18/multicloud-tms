package com.trackflow.tms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.tms.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchAndDashboardIntegrationTest extends AbstractIntegrationTest {

    private static final String LIVE = "t.deleted_at IS NULL AND p.deleted_at IS NULL";

    private long sqlCount(String where, Object... args) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tickets t JOIN projects p ON p.id = t.project_id WHERE " + LIVE + " AND " + where,
                Long.class, args);
        return count == null ? 0 : count;
    }

    @Test
    void filtersCombineWithAnd() throws Exception {
        JsonNode page = body(getAs(ADMIN,
                "/api/v1/tickets?projectId=1&status=OPEN&status=IN_PROGRESS&priority=CRITICAL&priority=HIGH&size=100")
                .andExpect(status().isOk()));
        page.get("content").forEach(t -> {
            assertThat(t.get("projectKey").asText()).isEqualTo("TMS");
            assertThat(t.get("status").asText()).isIn("OPEN", "IN_PROGRESS");
            assertThat(t.get("priority").asText()).isIn("CRITICAL", "HIGH");
        });
        assertThat(page.get("totalElements").asLong()).isEqualTo(sqlCount(
                "t.project_id = 1 AND t.status IN ('OPEN','IN_PROGRESS') AND t.priority IN ('CRITICAL','HIGH')"));
    }

    @Test
    void assigneeAndUnassignedFilters() throws Exception {
        JsonNode mine = body(getAs(ADMIN, "/api/v1/tickets?assigneeId=4&size=100"));
        mine.get("content").forEach(t -> assertThat(t.get("assignee").get("id").asLong()).isEqualTo(DEV2_ID));
        assertThat(mine.get("totalElements").asLong()).isEqualTo(sqlCount("t.assignee_id = 4"));

        // The seeded bug TMS-8 was "500 error when filtering by Unassigned".
        JsonNode unassigned = body(getAs(ADMIN, "/api/v1/tickets?unassigned=true&size=100").andExpect(status().isOk()));
        unassigned.get("content").forEach(t -> assertThat(t.has("assignee")).isFalse());
        assertThat(unassigned.get("totalElements").asLong()).isEqualTo(sqlCount("t.assignee_id IS NULL"));
    }

    @Test
    void textSearchMatchesTitleOrExactKey() throws Exception {
        JsonNode byKey = body(getAs(PM, "/api/v1/tickets?q=tms-4"));
        assertThat(byKey.get("content").findValuesAsText("key")).contains("TMS-4");

        JsonNode byTitle = body(getAs(PM, "/api/v1/tickets?q=refresh token"));
        assertThat(byTitle.get("content").findValuesAsText("key")).contains("TMS-2");

        JsonNode wildcard = body(getAs(PM, "/api/v1/tickets?q=%25"));     // a literal % is not a wildcard
        assertThat(wildcard.get("totalElements").asLong()).isZero();
    }

    @Test
    void sortByPriorityUsesSeverityNotAlphabet() throws Exception {
        JsonNode page = body(getAs(ADMIN, "/api/v1/tickets?sort=priority,desc&size=100"));
        List<Integer> severities = new ArrayList<>();
        page.get("content").forEach(t -> severities.add(
                List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").indexOf(t.get("priority").asText())));
        assertThat(severities).isSortedAccordingTo((a, b) -> b - a);
        assertThat(severities.get(0)).isEqualTo(3);
    }

    @Test
    void pagingIsCappedAndBadSortIsRejected() throws Exception {
        JsonNode page = body(getAs(ADMIN, "/api/v1/tickets?size=500"));
        assertThat(page.get("size").asInt()).isEqualTo(100);
        getAs(ADMIN, "/api/v1/tickets?sort=password").andExpect(status().isBadRequest());
        getAs(ADMIN, "/api/v1/users?sort=nonsense").andExpect(status().isBadRequest());
    }

    @Test
    void adminDashboardMatchesDirectSqlCounts() throws Exception {
        JsonNode dashboard = body(getAs(ADMIN, "/api/v1/dashboard").andExpect(status().isOk()));
        assertThat(dashboard.get("scope").asText()).isEqualTo("GLOBAL");

        JsonNode totals = dashboard.get("totals");
        assertThat(totals.get("total").asLong()).isEqualTo(sqlCount("1 = 1"));
        assertThat(totals.get("closed").asLong()).isEqualTo(sqlCount("t.status = 'CLOSED'"));
        assertThat(totals.get("open").asLong()).isEqualTo(sqlCount("t.status <> 'CLOSED'"));
        assertThat(totals.get("unassigned").asLong())
                .isEqualTo(sqlCount("t.status <> 'CLOSED' AND t.assignee_id IS NULL"));
        for (String s : List.of("OPEN", "IN_PROGRESS", "IN_REVIEW", "CLOSED")) {
            assertThat(dashboard.get("byStatus").get(s).asLong()).as(s).isEqualTo(sqlCount("t.status = ?", s));
        }
        for (String p : List.of("LOW", "MEDIUM", "HIGH", "CRITICAL")) {
            assertThat(dashboard.get("byPriority").get(p).asLong()).as(p).isEqualTo(sqlCount("t.priority = ?", p));
        }

        // Productivity: seeded closures by Sam (TMS-1, TMS-7, OPS-3) and Jordan (TMS-2) are within 4 weeks.
        JsonNode productivity = dashboard.get("productivity");
        assertThat(productivity.findValuesAsText("fullName")).contains("Sam Developer", "Jordan Developer");
        productivity.forEach(row -> assertThat(row.get("closedPerWeek")).hasSize(4));
        assertThat(dashboard.get("recentActivity")).isNotEmpty();
    }

    @Test
    void personalDashboardsOnlyCountOwnTickets() throws Exception {
        JsonNode user = body(getAs(USER, "/api/v1/dashboard"));
        assertThat(user.get("scope").asText()).isEqualTo("PERSONAL");
        assertThat(user.get("totals").get("total").asLong()).isEqualTo(sqlCount("t.reporter_id = 5"));
        assertThat(user.get("productivity")).isEmpty();

        JsonNode dev = body(getAs(DEV, "/api/v1/dashboard"));
        assertThat(dev.get("totals").get("total").asLong()).isEqualTo(sqlCount("t.assignee_id = 3"));

        JsonNode pm = body(getAs(PM, "/api/v1/dashboard?projectId=2&weeks=8"));
        assertThat(pm.get("scope").asText()).isEqualTo("PROJECTS");
        assertThat(pm.get("totals").get("total").asLong()).isEqualTo(sqlCount("t.project_id = 2"));

        getAs(USER, "/api/v1/dashboard?projectId=2").andExpect(status().isNotFound());
    }

    @Test
    void csvExportForReports() throws Exception {
        String csv = getAs(PM, "/api/v1/tickets/export?projectId=1")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"tickets-")))
                .andReturn().getResponse().getContentAsString();
        String[] lines = csv.split("\r\n");
        assertThat(lines[0]).isEqualTo("Key,Title,Type,Priority,Status,Project,Assignee,Reporter,Due date,Created,Closed");
        assertThat(lines.length - 1L).isEqualTo(sqlCount("t.project_id = 1"));
    }

    @Test
    void activityFeedIsScoped() throws Exception {
        JsonNode admin = body(getAs(ADMIN, "/api/v1/activity?size=100"));
        JsonNode user = body(getAs(USER, "/api/v1/activity?size=100"));
        assertThat(admin.get("totalElements").asLong()).isGreaterThan(user.get("totalElements").asLong());
        user.get("content").forEach(a -> assertThat(a.get("actor").get("id").asLong()).isEqualTo(USER_ID));
    }
}
