package com.trackflow.tms.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "admin@trackflow.dev")
        @NotBlank @Email
        String email,

        @Schema(example = "Password@123")
        @NotBlank
        String password) {
}
