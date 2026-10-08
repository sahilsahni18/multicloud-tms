package com.trackflow.tms.service.provisioning;

import com.trackflow.tms.entity.CloudProvider;
import com.trackflow.tms.entity.Deployment;
import com.trackflow.tms.entity.DeploymentAction;
import com.trackflow.tms.entity.DeploymentEnvironment;
import com.trackflow.tms.entity.RunnerType;

/** Immutable copy of what a runner needs; safe to hand to another thread. */
public record DeploymentRun(
        Long deploymentId,
        CloudProvider cloudProvider,
        String region,
        int clusterCount,
        DeploymentEnvironment environment,
        DeploymentAction action,
        RunnerType runner) {

    public static DeploymentRun of(Deployment d) {
        return new DeploymentRun(d.getId(), d.getCloudProvider(), d.getRegion(), d.getClusterCount(),
                d.getEnvironment(), d.getAction(), d.getRunner());
    }
}
