package com.trackflow.tms.service.provisioning;

import com.trackflow.tms.config.DeploymentProperties;
import com.trackflow.tms.entity.DeploymentStatus;
import com.trackflow.tms.entity.RunnerType;
import com.trackflow.tms.service.DeploymentService;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Starts {@code .github/workflows/provision.yml} with a workflow_dispatch. The
 * workflow does the work (namespace + database on the region's cluster for an
 * apply, {@code tofu plan} for a plan) and reports each step back through the
 * HMAC-signed callback endpoint. Only a failed dispatch is reported from here.
 */
@Slf4j
@Component
public class GitHubActionsProvisioningRunner implements ProvisioningRunner {

    private final DeploymentService deployments;
    private final DeploymentProperties.GitHub github;
    private final RestClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "github-dispatcher");
        thread.setDaemon(true);
        return thread;
    });

    public GitHubActionsProvisioningRunner(DeploymentService deployments, DeploymentProperties properties,
                                           RestClient.Builder builder) {
        this.deployments = deployments;
        this.github = properties.github();
        this.client = builder
                .baseUrl(StringUtils.hasText(github.apiUrl()) ? github.apiUrl() : "https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    @Override
    public RunnerType type() {
        return RunnerType.GITHUB_ACTIONS;
    }

    @Override
    public void start(DeploymentRun run) {
        executor.submit(() -> dispatch(run));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    void dispatch(DeploymentRun run) {
        String missing = missingSetting();
        if (missing != null) {
            fail(run, "GitHub Actions runner is not configured: " + missing + " is not set");
            return;
        }
        String[] repo = github.repository().split("/", 2);
        try {
            client.post()
                    .uri("/repos/{owner}/{name}/actions/workflows/{workflow}/dispatches", repo[0], repo[1],
                            github.workflow())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + github.token())
                    .body(Map.of("ref", github.ref(), "inputs", inputs(run, github.callbackUrl())))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Dispatched {} for deployment {}", github.workflow(), run.deploymentId());
        } catch (RestClientResponseException e) {
            fail(run, "GitHub rejected the workflow dispatch (HTTP " + e.getStatusCode().value() + "): "
                    + abbreviate(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            fail(run, "Could not reach GitHub to start the workflow: " + e.getMessage());
        }
    }

    /** workflow_dispatch inputs; the names match provision.yml. */
    static Map<String, String> inputs(DeploymentRun run, String callbackUrl) {
        Map<String, String> inputs = new LinkedHashMap<>();
        inputs.put("deployment_id", String.valueOf(run.deploymentId()));
        inputs.put("action", run.action().name().toLowerCase(Locale.ROOT));
        inputs.put("cloud", run.cloudProvider().name().toLowerCase(Locale.ROOT));
        inputs.put("region", run.region());
        inputs.put("environment", run.environment().name().toLowerCase(Locale.ROOT));
        inputs.put("cluster_count", String.valueOf(run.clusterCount()));
        inputs.put("callback_url", stripTrailingSlashes(callbackUrl));
        return inputs;
    }

    static String stripTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    private String missingSetting() {
        if (!StringUtils.hasText(github.token())) {
            return "DEPLOY_GITHUB_TOKEN";
        }
        if (!StringUtils.hasText(github.callbackUrl())) {
            return "DEPLOY_CALLBACK_URL";
        }
        if (!StringUtils.hasText(github.repository()) || !github.repository().contains("/")) {
            return "DEPLOY_GITHUB_REPOSITORY (owner/name)";
        }
        if (!StringUtils.hasText(github.workflow()) || !StringUtils.hasText(github.ref())) {
            return "DEPLOY_GITHUB_WORKFLOW / DEPLOY_GITHUB_REF";
        }
        return null;
    }

    private void fail(DeploymentRun run, String message) {
        log.warn("Deployment {} failed to start: {}", run.deploymentId(), message);
        try {
            deployments.applyEvent(run.deploymentId(), DeploymentStatus.FAILED, message, null, null);
        } catch (RuntimeException e) {
            log.warn("Could not mark deployment {} as failed: {}", run.deploymentId(), e.getMessage());
        }
    }

    private static String abbreviate(String text) {
        String flat = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "…" : flat;
    }
}
