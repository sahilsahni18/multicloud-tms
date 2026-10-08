package com.trackflow.tms.dto.deployment;

import com.trackflow.tms.entity.CloudProvider;
import com.trackflow.tms.entity.DeploymentEnvironment;
import com.trackflow.tms.entity.RunnerType;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything the Deployment Portal form needs to render its dropdowns. */
public record DeploymentOptionsResponse(
        Map<CloudProvider, List<String>> regions,
        List<DeploymentEnvironment> environments,
        Set<DeploymentEnvironment> applyEnvironments,
        int minClusters,
        int maxClusters,
        RunnerType runner) {
}
