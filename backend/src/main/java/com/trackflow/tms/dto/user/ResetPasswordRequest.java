package com.trackflow.tms.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin sets a new password for someone who forgot theirs. */
public record ResetPasswordRequest(
        @Schema(example = "Temporary@2026", description = "8-72 characters; tell the user to change it under Profile")
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
