package com.trackflow.tms.dto.deployment;

import com.trackflow.tms.dto.common.UserRef;
import com.trackflow.tms.entity.CloudProvider;
import com.trackflow.tms.entity.Deployment;
import com.trackflow.tms.entity.DeploymentAction;
import com.trackflow.tms.entity.DeploymentEnvironment;
import com.trackflow.tms.entity.DeploymentStatus;
import com.trackflow.tms.entity.RunnerType;
import java.time.Instant;

/** One row of the deployment history. */
public record DeploymentResponse(
        Long id,
        CloudProvider cloudProvider,
        String region,
        int clusterCount,
        DeploymentEnvironment environment,
        DeploymentAction action,
        DeploymentStatus status,
        RunnerType runner,
        String externalRunUrl,
        String errorMessage,
        UserRef requestedBy,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {

    public static DeploymentResponse from(Deployment d) {
        return new DeploymentResponse(d.getId(), d.getCloudProvider(), d.getRegion(), d.getClusterCount(),
                d.getEnvironment(), d.getAction(), d.getStatus(), d.getRunner(), d.getExternalRunUrl(),
                d.getErrorMessage(), UserRef.of(d.getRequestedBy()), d.getCreatedAt(), d.getStartedAt(),
                d.getFinishedAt());
    }
}
