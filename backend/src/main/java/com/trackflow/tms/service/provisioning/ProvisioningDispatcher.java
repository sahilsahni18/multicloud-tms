package com.trackflow.tms.service.provisioning;

import com.trackflow.tms.entity.DeploymentStatus;
import com.trackflow.tms.entity.RunnerType;
import com.trackflow.tms.service.DeploymentService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands accepted deployments to the matching runner, only after the request
 * has committed (so the runner never sees a deployment that might roll back).
 */
@Slf4j
@Component
public class ProvisioningDispatcher {

    private final Map<RunnerType, ProvisioningRunner> runners = new EnumMap<>(RunnerType.class);
    private final DeploymentService deployments;

    public ProvisioningDispatcher(List<ProvisioningRunner> runners, DeploymentService deployments) {
        runners.forEach(runner -> this.runners.put(runner.type(), runner));
        this.deployments = deployments;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onStartRequested(DeploymentEvents.StartRequested event) {
        DeploymentRun run = event.run();
        ProvisioningRunner runner = runners.get(run.runner());
        if (runner == null) {
            log.error("No provisioning runner for {}; deployment {} failed", run.runner(), run.deploymentId());
            deployments.applyEvent(run.deploymentId(), DeploymentStatus.FAILED,
                    "No provisioning runner of type " + run.runner() + " is configured", null, null);
            return;
        }
        log.info("Starting deployment {} ({} {} {} {}) with {}", run.deploymentId(), run.action(),
                run.cloudProvider(), run.region(), run.environment(), run.runner());
        runner.start(run);
    }
}
