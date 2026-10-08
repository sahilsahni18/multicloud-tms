package com.trackflow.tms.dto.deployment;

import com.trackflow.tms.entity.DeploymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Step report posted by the provisioning pipeline (GitHub Actions). */
public record DeploymentCallbackRequest(
        @NotNull DeploymentStatus status,
        @Size(max = 2000) String message,
        @Size(max = 64) String runId,
        @Size(max = 512) String runUrl) {
}
