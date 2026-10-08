package com.trackflow.tms.service.provisioning;

/** Spring application events published by DeploymentService, handled after commit. */
public final class DeploymentEvents {

    private DeploymentEvents() {
    }

    /** A deployment (or destroy) was accepted and should be handed to its runner. */
    public record StartRequested(DeploymentRun run) {
    }

    /** A deployment's status changed; live subscribers should be refreshed. */
    public record Changed(Long deploymentId) {
    }
}
