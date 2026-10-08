package com.trackflow.tms.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "new.user@trackflow.dev")
        @NotBlank @Email @Size(max = 255)
        String email,

        @Schema(example = "Password@123", description = "8-72 characters")
        @NotBlank @Size(min = 8, max = 72)
        String password,

        @Schema(example = "New User")
        @NotBlank @Size(min = 2, max = 120)
        String fullName) {
}
