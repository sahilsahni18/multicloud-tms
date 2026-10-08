package com.trackflow.tms.dto.deployment;

import com.trackflow.tms.entity.CloudProvider;
import com.trackflow.tms.entity.DeploymentAction;
import com.trackflow.tms.entity.DeploymentEnvironment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDeploymentRequest(
        @Schema(example = "AWS") @NotNull CloudProvider cloudProvider,
        @Schema(example = "us-east-1") @NotBlank String region,
        @Schema(example = "1") @NotNull @Min(1) @Max(5) Integer clusterCount,
        @Schema(example = "DEV") @NotNull DeploymentEnvironment environment,
        @Schema(description = "APPLY (default) or PLAN") DeploymentAction action,
        @Schema(description = "Required for PROD: type PROD to confirm") String confirmEnvironment) {
}
