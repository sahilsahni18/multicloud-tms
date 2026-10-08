package com.trackflow.tms.dto.deployment;

import com.trackflow.tms.entity.DeploymentStatus;
import java.time.Instant;
import java.util.List;

/**
 * A deployment with its event log and the step list for the UI stepper
 * ({@code expectedSteps} depends on the action: apply, plan or destroy).
 */
public record DeploymentDetailResponse(
        DeploymentResponse deployment,
        List<DeploymentStatus> expectedSteps,
        List<EventResponse> events) {

    public record EventResponse(Long id, DeploymentStatus status, String message, Instant occurredAt) {
    }
}
