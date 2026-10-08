package com.trackflow.tms.service.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import com.trackflow.tms.entity.CloudProvider;
import com.trackflow.tms.entity.DeploymentAction;
import com.trackflow.tms.entity.DeploymentEnvironment;
import com.trackflow.tms.entity.DeploymentStatus;
import com.trackflow.tms.entity.RunnerType;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulatedProvisioningRunnerTest {

    private static DeploymentRun run(CloudProvider cloud, DeploymentAction action, int clusters) {
        return new DeploymentRun(1L, cloud, "eastus", clusters, DeploymentEnvironment.DEV, action, RunnerType.SIMULATED);
    }

    @Test
    void applyFollowsThePortalSteps() {
        List<SimulatedProvisioningRunner.Step> steps =
                SimulatedProvisioningRunner.steps(run(CloudProvider.AZURE, DeploymentAction.APPLY, 3));
        assertThat(steps).extracting(SimulatedProvisioningRunner.Step::status)
                .containsExactlyElementsOf(DeploymentStatus.APPLY_STEPS.subList(1, DeploymentStatus.APPLY_STEPS.size()));
        assertThat(steps.get(1).message()).contains("3 AKS cluster(s)");
        assertThat(steps.get(4).message()).contains("Static Web Apps");
    }

    @Test
    void awsMessagesNameAwsServices() {
        List<SimulatedProvisioningRunner.Step> steps =
                SimulatedProvisioningRunner.steps(run(CloudProvider.AWS, DeploymentAction.APPLY, 1));
        assertThat(steps.get(0).message()).contains("VPC");
        assertThat(steps.get(1).message()).contains("EKS");
        assertThat(steps.get(2).message()).contains("RDS");
        assertThat(steps.get(4).message()).contains("CloudFront");
    }

    @Test
    void planAndDestroyAreSingleSteps() {
        assertThat(SimulatedProvisioningRunner.steps(run(CloudProvider.AWS, DeploymentAction.PLAN, 1)))
                .extracting(SimulatedProvisioningRunner.Step::status).containsExactly(DeploymentStatus.PLANNED);
        assertThat(SimulatedProvisioningRunner.steps(run(CloudProvider.AWS, DeploymentAction.DESTROY, 1)))
                .extracting(SimulatedProvisioningRunner.Step::status).containsExactly(DeploymentStatus.DESTROYED);
    }
}
