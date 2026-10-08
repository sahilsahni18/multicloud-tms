package com.trackflow.tms.dto.user;

import com.trackflow.tms.entity.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
        @Schema(example = "riya@trackflow.dev") @NotBlank @Email @Size(max = 255) String email,
        @Schema(example = "Riya Developer") @NotBlank @Size(min = 2, max = 120) String fullName,
        @Schema(example = "Password@123") @NotBlank @Size(min = 8, max = 72) String password,
        @Schema(example = "[\"DEVELOPER\"]") @NotEmpty Set<RoleName> roles,
        @Schema(description = "Defaults to true") Boolean enabled) {
}
