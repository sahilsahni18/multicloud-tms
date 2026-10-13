package com.trackflow.tms.config;

import com.trackflow.tms.entity.CloudProvider;
import com.trackflow.tms.entity.DeploymentEnvironment;
import com.trackflow.tms.entity.RunnerType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Deployment Portal settings ({@code app.deployment.*}).
 *
 * @param runner             who runs OpenTofu: SIMULATED (local), GITHUB_ACTIONS (cloud)
 * @param simulatedStepDelay pause between simulated steps
 * @param callbackSecret     HMAC key shared with the provisioning workflow; blank disables callbacks
 * @param callbackMaxSkew    how old a signed callback may be (replay protection)
 * @param applyEnvironments  environments that may run APPLY; the others are PLAN-only
 * @param regions            selectable regions per cloud
 * @param github             where the GITHUB_ACTIONS runner dispatches the provisioning workflow
 */
@Validated
@ConfigurationProperties(prefix = "app.deployment")
public record DeploymentProperties(
        @NotNull RunnerType runner,
        @NotNull Duration simulatedStepDelay,
        String callbackSecret,
        @NotNull Duration callbackMaxSkew,
        @NotEmpty Set<DeploymentEnvironment> applyEnvironments,
        @NotEmpty Map<CloudProvider, List<String>> regions,
        GitHub github) {

    public DeploymentProperties {
        github = github == null ? new GitHub(null, null, null, null, null, null) : github;
    }

    /**
     * @param token       fine-grained token with Actions read/write on the repository
     * @param repository  {@code owner/name}
     * @param workflow    workflow file name ({@code provision.yml})
     * @param ref         branch the workflow runs from
     * @param callbackUrl public base URL of this backend, where the workflow reports steps
     * @param apiUrl      GitHub REST API base URL
     */
    public record GitHub(String token, String repository, String workflow, String ref,
                         String callbackUrl, String apiUrl) {
    }
}
