package com.trackflow.tms.dto.project;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @Schema(example = "WEB", description = "2-10 upper-case letters/digits, starts with a letter; immutable")
        @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9]{1,9}$", message = "must be 2-10 letters or digits, starting with a letter")
        String key,

        @Schema(example = "Customer Website") @NotBlank @Size(max = 120) String name,

        @Size(max = 5000) String description,

        @Schema(description = "Admin only: make another PM/admin the owner. Defaults to the caller.")
        Long ownerId) {
}
