package com.trackflow.tms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.tms.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Acceptance check "each role gets 403 on forbidden APIs": every role-guarded
 * endpoint is called by every role that the PRD permission matrix excludes.
 * Bodies are valid, so a 403 can only come from the role check.
 */
class RbacIntegrationTest extends AbstractIntegrationTest {

    record Call(String name, MockHttpServletRequestBuilder request, List<String> forbiddenFor) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Arguments> forbiddenCalls() {
        List<String> nonAdmins = List.of(PM, DEV, USER);
        List<String> notAdminOrPm = List.of(DEV, USER);
        List<Call> calls = List.of(
                new Call("list users", get("/api/v1/users"), nonAdmins),
                new Call("get user", get("/api/v1/users/5"), nonAdmins),
                new Call("create user", post("/api/v1/users").content("""
                        {"email":"x@y.dev","fullName":"X Y","password":"Password@123","roles":["USER"]}"""), nonAdmins),
                new Call("update user", put("/api/v1/users/5").content("""
                        {"email":"user@trackflow.dev","fullName":"Casey User","enabled":true}"""), nonAdmins),
                new Call("change roles", put("/api/v1/users/5/roles").content("""
                        {"roles":["ADMIN"]}"""), nonAdmins),
                new Call("delete user", delete("/api/v1/users/5"), nonAdmins),
                new Call("reset password", put("/api/v1/users/5/password").content("""
                        {"newPassword":"Hijacked@123"}"""), nonAdmins),
                new Call("list roles", get("/api/v1/roles"), nonAdmins),
                new Call("user lookup", get("/api/v1/users/lookup"), notAdminOrPm),
                new Call("create project", post("/api/v1/projects").content("""
                        {"key":"NOPE","name":"Nope"}"""), notAdminOrPm),
                new Call("update project", put("/api/v1/projects/1").content("""
                        {"name":"Renamed"}"""), notAdminOrPm),
                new Call("delete project", delete("/api/v1/projects/1"), nonAdmins),
                new Call("add member", post("/api/v1/projects/1/members").content("""
                        {"userId":1}"""), notAdminOrPm),
                new Call("remove member", delete("/api/v1/projects/1/members/4"), notAdminOrPm),
                new Call("assign ticket", put("/api/v1/tickets/6/assignee").content("""
                        {"assigneeId":3}"""), notAdminOrPm),
                new Call("delete ticket", delete("/api/v1/tickets/6"), notAdminOrPm),
                new Call("export CSV", get("/api/v1/tickets/export"), notAdminOrPm),
                new Call("deploy options", get("/api/v1/deployments/options"), nonAdmins),
                new Call("deploy", post("/api/v1/deployments").content("""
                        {"cloudProvider":"AWS","region":"us-east-1","clusterCount":1,"environment":"DEV"}"""), nonAdmins),
                new Call("deployment history", get("/api/v1/deployments"), nonAdmins),
                new Call("deployment detail", get("/api/v1/deployments/1"), nonAdmins),
                new Call("destroy", post("/api/v1/deployments/1/destroy"), nonAdmins));
        return calls.stream().flatMap(call -> call.forbiddenFor().stream().map(email -> Arguments.of(call, email)));
    }

    @ParameterizedTest(name = "{1} -> {0} = 403")
    @MethodSource("forbiddenCalls")
    void roleWithoutPermissionGets403(Call call, String email) throws Exception {
        mvc.perform(as(email, call.request()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void allowedRolesGetThrough() throws Exception {
        getAs(ADMIN, "/api/v1/users").andExpect(status().isOk());
        getAs(ADMIN, "/api/v1/roles").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        getAs(ADMIN, "/api/v1/deployments/options").andExpect(status().isOk());
        getAs(PM, "/api/v1/users/lookup?q=dev").andExpect(status().isOk());
        getAs(PM, "/api/v1/tickets/export").andExpect(status().isOk());
        for (String email : List.of(ADMIN, PM, DEV, USER)) {
            getAs(email, "/api/v1/tickets").andExpect(status().isOk());
            getAs(email, "/api/v1/projects").andExpect(status().isOk());
            getAs(email, "/api/v1/dashboard").andExpect(status().isOk());
            getAs(email, "/api/v1/activity").andExpect(status().isOk());
        }
    }

    @Test
    void everyBusinessEndpointRequiresAToken() throws Exception {
        for (String url : List.of("/api/v1/users", "/api/v1/projects", "/api/v1/tickets", "/api/v1/tickets/1",
                "/api/v1/dashboard", "/api/v1/activity", "/api/v1/deployments", "/api/v1/roles")) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void projectListIsScopedByMembership() throws Exception {
        assertThat(projectKeys(ADMIN)).contains("TMS", "OPS");
        assertThat(projectKeys(DEV)).contains("TMS", "OPS");
        assertThat(projectKeys(USER)).contains("TMS").doesNotContain("OPS");
        getAs(USER, "/api/v1/projects/{id}", OPS).andExpect(status().isNotFound());
    }

    @Test
    void ticketVisibilityFollowsTheMatrix() throws Exception {
        // USER sees only tickets they reported.
        JsonNode mine = body(getAs(USER, "/api/v1/tickets?size=100"));
        assertThat(mine.get("totalElements").asLong()).isPositive();
        mine.get("content").forEach(t -> assertThat(t.get("reporter").get("id").asLong()).isEqualTo(USER_ID));
        getAs(USER, "/api/v1/tickets/{id}", 1).andExpect(status().isNotFound());   // TMS-1, reported by pm
        getAs(USER, "/api/v1/tickets/key/TMS-1").andExpect(status().isNotFound());

        // Developers and managers see every ticket of their projects.
        getAs(DEV, "/api/v1/tickets/{id}", 1).andExpect(status().isOk());
        getAs(DEV, "/api/v1/tickets/{id}", 10).andExpect(status().isOk());          // OPS-2, unassigned
        getAs(PM, "/api/v1/tickets/key/ops-2").andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("OPS-2"));

        // Nobody is told whether a hidden ticket exists.
        getAs(USER, "/api/v1/tickets/{id}", 999_999).andExpect(status().isNotFound());
    }

    @Test
    void rowLevelRulesReturn403ForVisibleTickets() throws Exception {
        // TMS-3 is IN_REVIEW and assigned to dev: dev may send it back but not close it.
        postAs(DEV, Map.of("status", "CLOSED"), "/api/v1/tickets/{id}/transitions", 3)
                .andExpect(status().isForbidden());
        // TMS-4 belongs to dev2: dev can see it but not move it.
        postAs(DEV, Map.of("status", "IN_REVIEW"), "/api/v1/tickets/{id}/transitions", 4)
                .andExpect(status().isForbidden());
        // pm is a member of OPS but not its owner: may assign its tickets, may not edit the project.
        putAs(PM, Map.of("name", "Renamed"), "/api/v1/projects/{id}", OPS).andExpect(status().isForbidden());
        // dev cannot edit a ticket assigned to someone else.
        JsonNode tms4 = body(getAs(DEV, "/api/v1/tickets/{id}", 4));
        putAs(DEV, Map.of("title", "x", "type", "BUG", "priority", "LOW", "version", tms4.get("version").asLong()),
                "/api/v1/tickets/{id}", 4).andExpect(status().isForbidden());
        assertThat(tms4.get("permissions").get("canEdit").asBoolean()).isFalse();
        assertThat(tms4.get("allowedTransitions")).isEmpty();
    }

    private List<String> projectKeys(String email) throws Exception {
        JsonNode page = body(getAs(email, "/api/v1/projects?size=100"));
        return page.get("content").findValuesAsText("key");
    }
}
