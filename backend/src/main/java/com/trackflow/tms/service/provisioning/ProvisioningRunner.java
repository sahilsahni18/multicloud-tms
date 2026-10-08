package com.trackflow.tms.service.provisioning;

import com.trackflow.tms.entity.RunnerType;

/**
 * Executes a deployment (OpenTofu + kubectl) somewhere else and reports each
 * step back through {@link com.trackflow.tms.service.DeploymentService#applyEvent}.
 * {@link #start} must return quickly; the work happens asynchronously.
 */
public interface ProvisioningRunner {

    RunnerType type();

    void start(DeploymentRun run);
}
