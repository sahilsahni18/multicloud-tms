package com.trackflow.tms.dto.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(@Schema(example = "Reproduced on staging.") @NotBlank @Size(max = 10000) String body) {
}
