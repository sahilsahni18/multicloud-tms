package com.trackflow.tms.service.provisioning;

import com.trackflow.tms.config.DeploymentProperties;
import com.trackflow.tms.entity.CloudProvider;
import com.trackflow.tms.entity.DeploymentStatus;
import com.trackflow.tms.entity.RunnerType;
import com.trackflow.tms.service.DeploymentService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Walks a deployment through its steps on a timer without touching any cloud,
 * so the portal can be demonstrated and tested locally. The messages mirror
 * what the real OpenTofu pipeline creates.
 */
@Slf4j
@Component
public class SimulatedProvisioningRunner implements ProvisioningRunner {

    private final DeploymentService deployments;
    private final Duration stepDelay;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "simulated-provisioner");
        thread.setDaemon(true);
        return thread;
    });

    public SimulatedProvisioningRunner(DeploymentService deployments, DeploymentProperties properties) {
        this.deployments = deployments;
        this.stepDelay = properties.simulatedStepDelay();
    }

    @Override
    public RunnerType type() {
        return RunnerType.SIMULATED;
    }

    @Override
    public void start(DeploymentRun run) {
        List<Step> steps = steps(run);
        executor.submit(() -> {
            try {
                for (Step step : steps) {
                    Thread.sleep(stepDelay.toMillis());
                    deployments.applyEvent(run.deploymentId(), step.status(), step.message(), null, null);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                log.warn("Simulated deployment {} stopped: {}", run.deploymentId(), e.getMessage());
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    static List<Step> steps(DeploymentRun run) {
        String env = run.environment().name().toLowerCase(Locale.ROOT);
        boolean aws = run.cloudProvider() == CloudProvider.AWS;
        List<Step> steps = new ArrayList<>();
        switch (run.action()) {
            case PLAN -> steps.add(new Step(DeploymentStatus.PLANNED,
                    "Plan: " + (14 + 6 * run.clusterCount()) + " to add, 0 to change, 0 to destroy (simulated)"));
            case DESTROY -> steps.add(new Step(DeploymentStatus.DESTROYED,
                    "Destroyed all tms-" + env + " resources in " + run.region() + " (simulated)"));
            case APPLY -> {
                steps.add(new Step(DeploymentStatus.NETWORK_CREATED, aws
                        ? "VPC 10.20.0.0/16 with 2 public subnets in " + run.region()
                        : "VNet 10.30.0.0/16 with AKS subnet in " + run.region()));
                steps.add(new Step(DeploymentStatus.CLUSTER_CREATED, run.clusterCount()
                        + (aws ? " EKS cluster(s) tms-" + env + " (1 x t3.medium node each)"
                               : " AKS cluster(s) tms-" + env + " (1 x Standard_B2s node each)")));
                steps.add(new Step(DeploymentStatus.DATABASE_CREATED, aws
                        ? "RDS MySQL 8.0 db.t3.micro tms-" + env
                        : "Azure Database for MySQL Flexible Server B1ms tms-" + env));
                steps.add(new Step(DeploymentStatus.BACKEND_DEPLOYED,
                        "trackflow-backend rolled out: 2/2 pods ready, /actuator/health UP"));
                steps.add(new Step(DeploymentStatus.FRONTEND_DEPLOYED, aws
                        ? "SPA uploaded to S3, CloudFront cache invalidated"
                        : "SPA deployed to Azure Static Web Apps"));
                steps.add(new Step(DeploymentStatus.COMPLETED, "Environment tms-" + env + " is ready (simulated)"));
            }
        }
        return steps;
    }

    record Step(DeploymentStatus status, String message) {
    }
}
