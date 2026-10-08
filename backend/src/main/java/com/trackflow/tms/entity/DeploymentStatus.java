package com.trackflow.tms.entity;

import java.util.List;

/**
 * Provisioning steps as reported by the runner. Terminal states accept no
 * further events (a destroy request re-opens COMPLETED / FAILED as DESTROYING).
 */
public enum DeploymentStatus {
    QUEUED(false),
    NETWORK_CREATED(false),
    CLUSTER_CREATED(false),
    DATABASE_CREATED(false),
    BACKEND_DEPLOYED(false),
    FRONTEND_DEPLOYED(false),
    COMPLETED(true),
    PLANNED(true),
    FAILED(true),
    DESTROYING(false),
    DESTROYED(true);

    public static final List<DeploymentStatus> APPLY_STEPS = List.of(
            QUEUED, NETWORK_CREATED, CLUSTER_CREATED, DATABASE_CREATED, BACKEND_DEPLOYED, FRONTEND_DEPLOYED, COMPLETED);
    public static final List<DeploymentStatus> PLAN_STEPS = List.of(QUEUED, PLANNED);
    public static final List<DeploymentStatus> DESTROY_STEPS = List.of(DESTROYING, DESTROYED);

    private final boolean terminal;

    DeploymentStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
