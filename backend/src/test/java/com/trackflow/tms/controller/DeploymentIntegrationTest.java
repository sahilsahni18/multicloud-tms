package com.trackflow.tms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.trackflow.tms.AbstractIntegrationTest;
import com.trackflow.tms.entity.CloudProvider;
import com.trackflow.tms.entity.Deployment;
import com.trackflow.tms.entity.DeploymentAction;
import com.trackflow.tms.entity.DeploymentEnvironment;
import com.trackflow.tms.entity.DeploymentStatus;
import com.trackflow.tms.entity.RunnerType;
import com.trackflow.tms.repository.DeploymentRepository;
import com.trackflow.tms.repository.UserRepository;
import com.trackflow.tms.security.DeploymentCallbackVerifier;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class DeploymentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeploymentCallbackVerifier verifier;

    private JsonNode deploy(Map<String, Object> request) throws Exception {
        return body(postAs(ADMIN, request, "/api/v1/deployments").andExpect(status().isAccepted()));
    }

    private JsonNode awaitStatus(long id, String expected) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> body(getAs(ADMIN, "/api/v1/deployments/{id}", id))
                        .get("deployment").get("status").asText().equals(expected));
        try {
            return body(getAs(ADMIN, "/api/v1/deployments/{id}", id));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> eventStatuses(JsonNode detail) {
        List<String> statuses = new ArrayList<>();
        detail.get("events").forEach(e -> statuses.add(e.get("status").asText()));
        return statuses;
    }

    @Test
    void oneClickDeployWalksThroughEveryStepAndLandsInHistory() throws Exception {
        JsonNode accepted = deploy(Map.of("cloudProvider", "AWS", "region", "us-east-1", "clusterCount", 1,
                "environment", "DEV"));
        long id = accepted.get("deployment").get("id").asLong();
        assertThat(accepted.get("deployment").get("status").asText()).isEqualTo("QUEUED");
        assertThat(accepted.get("deployment").get("runner").asText()).isEqualTo("SIMULATED");
        assertThat(accepted.get("expectedSteps").toString()).contains("NETWORK_CREATED", "FRONTEND_DEPLOYED");

        JsonNode done = awaitStatus(id, "COMPLETED");
        assertThat(eventStatuses(done)).containsExactly("QUEUED", "NETWORK_CREATED", "CLUSTER_CREATED",
                "DATABASE_CREATED", "BACKEND_DEPLOYED", "FRONTEND_DEPLOYED", "COMPLETED");
        assertThat(done.get("deployment").get("startedAt").asText()).isNotBlank();
        assertThat(done.get("deployment").get("finishedAt").asText()).isNotBlank();
        assertThat(done.get("deployment").get("requestedBy").get("email").asText()).isEqualTo(ADMIN);

        JsonNode history = body(getAs(ADMIN, "/api/v1/deployments"));
        JsonNode first = history.get("content").get(0);
        assertThat(first.get("id").asLong()).isEqualTo(id);
        assertThat(first.get("cloudProvider").asText()).isEqualTo("AWS");
        assertThat(first.get("clusterCount").asInt()).isEqualTo(1);

        // Destroy it.
        JsonNode destroying = body(postAs(ADMIN, Map.of(), "/api/v1/deployments/{id}/destroy", id)
                .andExpect(status().isAccepted()));
        assertThat(destroying.get("deployment").get("status").asText()).isEqualTo("DESTROYING");
        JsonNode destroyed = awaitStatus(id, "DESTROYED");
        assertThat(eventStatuses(destroyed)).endsWith("DESTROYING", "DESTROYED");
        postAs(ADMIN, Map.of(), "/api/v1/deployments/{id}/destroy", id).andExpect(status().isConflict());
    }

    @Test
    void qaAndProdArePlanOnlyAndProdNeedsConfirmation() throws Exception {
        postAs(ADMIN, Map.of("cloudProvider", "AZURE", "region", "eastus", "clusterCount", 2, "environment", "QA"),
                "/api/v1/deployments")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("Use action PLAN")));
        postAs(ADMIN, Map.of("cloudProvider", "AZURE", "region", "eastus", "clusterCount", 1, "environment", "PROD",
                "action", "PLAN"), "/api/v1/deployments")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("Type PROD")));

        JsonNode plan = deploy(Map.of("cloudProvider", "AZURE", "region", "westeurope", "clusterCount", 2,
                "environment", "PROD", "action", "PLAN", "confirmEnvironment", "PROD"));
        long id = plan.get("deployment").get("id").asLong();
        JsonNode planned = awaitStatus(id, "PLANNED");
        assertThat(eventStatuses(planned)).containsExactly("QUEUED", "PLANNED");
        assertThat(planned.get("events").get(1).get("message").asText()).contains("26 to add");
        postAs(ADMIN, Map.of(), "/api/v1/deployments/{id}/destroy", id).andExpect(status().isConflict());
    }

    @Test
    void requestIsValidated() throws Exception {
        Map<String, Object> base = new HashMap<>(Map.of("cloudProvider", "AWS", "region", "us-east-1",
                "clusterCount", 1, "environment", "DEV"));
        Map<String, Object> badRegion = new HashMap<>(base);
        badRegion.put("region", "ap-south-1");
        postAs(ADMIN, badRegion, "/api/v1/deployments").andExpect(status().isBadRequest());
        Map<String, Object> tooMany = new HashMap<>(base);
        tooMany.put("clusterCount", 6);
        postAs(ADMIN, tooMany, "/api/v1/deployments").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("clusterCount"));
        Map<String, Object> destroy = new HashMap<>(base);
        destroy.put("action", "DESTROY");
        postAs(ADMIN, destroy, "/api/v1/deployments").andExpect(status().isBadRequest());

        JsonNode options = body(getAs(ADMIN, "/api/v1/deployments/options"));
        assertThat(options.get("regions").get("AWS").toString()).contains("us-east-1", "us-west-2");
        assertThat(options.get("regions").get("AZURE").toString()).contains("eastus", "westeurope");
        assertThat(options.get("maxClusters").asInt()).isEqualTo(5);
    }

    @Test
    void liveStatusStreamIsServerSentEvents() throws Exception {
        JsonNode accepted = deploy(Map.of("cloudProvider", "AZURE", "region", "eastus", "clusterCount", 1,
                "environment", "DEV"));
        long id = accepted.get("deployment").get("id").asLong();
        mvc.perform(as(ADMIN, get("/api/v1/deployments/{id}/stream", id)).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());
        awaitStatus(id, "COMPLETED");
        mvc.perform(as(DEV, get("/api/v1/deployments/{id}/stream", id))).andExpect(status().isForbidden());
    }

    /** A deployment owned by the GitHub Actions runner: nothing advances it except signed callbacks. */
    private long externalDeployment() {
        Deployment d = new Deployment();
        d.setCloudProvider(CloudProvider.AWS);
        d.setRegion("us-west-2");
        d.setClusterCount(1);
        d.setEnvironment(DeploymentEnvironment.QA);
        d.setAction(DeploymentAction.APPLY);
        d.setStatus(DeploymentStatus.QUEUED);
        d.setRunner(RunnerType.GITHUB_ACTIONS);
        d.setRequestedBy(userRepository.getReferenceById(ADMIN_ID));
        return deploymentRepository.save(d).getId();
    }

    private int callback(long id, String body, String timestamp, String signature) throws Exception {
        return mvc.perform(post("/api/v1/deployments/{id}/events", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(DeploymentCallbackVerifier.TIMESTAMP_HEADER, timestamp)
                        .header(DeploymentCallbackVerifier.SIGNATURE_HEADER, signature)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void signedPipelineCallbacksAdvanceTheDeployment() throws Exception {
        long id = externalDeployment();
        String now = String.valueOf(Instant.now().getEpochSecond());
        String body = """
                {"status":"NETWORK_CREATED","message":"tofu apply -target=module.vpc done",\
                "runId":"123456","runUrl":"https://github.com/acme/multicloud-tms/actions/runs/123456"}""";

        assertThat(callback(id, body, now, "sha256=deadbeef")).isEqualTo(401);
        assertThat(callback(id, body, now, verifier.sign(now, body.replace("vpc", "eks")))).isEqualTo(401);
        String stale = String.valueOf(Instant.now().minusSeconds(600).getEpochSecond());
        assertThat(callback(id, body, stale, verifier.sign(stale, body))).isEqualTo(401);

        assertThat(callback(id, body, now, verifier.sign(now, body))).isEqualTo(204);
        JsonNode detail = body(getAs(ADMIN, "/api/v1/deployments/{id}", id));
        assertThat(detail.get("deployment").get("status").asText()).isEqualTo("NETWORK_CREATED");
        assertThat(detail.get("deployment").get("externalRunUrl").asText()).endsWith("/runs/123456");

        String wrongStep = "{\"status\":\"PLANNED\"}";
        assertThat(callback(id, wrongStep, now, verifier.sign(now, wrongStep))).isEqualTo(400);

        String failed = "{\"status\":\"FAILED\",\"message\":\"EKS quota exceeded\"}";
        assertThat(callback(id, failed, now, verifier.sign(now, failed))).isEqualTo(204);
        detail = body(getAs(ADMIN, "/api/v1/deployments/{id}", id));
        assertThat(detail.get("deployment").get("status").asText()).isEqualTo("FAILED");
        assertThat(detail.get("deployment").get("errorMessage").asText()).isEqualTo("EKS quota exceeded");

        String late = "{\"status\":\"CLUSTER_CREATED\"}";
        assertThat(callback(id, late, now, verifier.sign(now, late))).isEqualTo(409);
    }
}
