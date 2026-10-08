package com.trackflow.tms.dto.user;

import com.trackflow.tms.entity.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UpdateRolesRequest(
        @Schema(example = "[\"PROJECT_MANAGER\", \"DEVELOPER\"]") @NotEmpty Set<RoleName> roles) {
}
