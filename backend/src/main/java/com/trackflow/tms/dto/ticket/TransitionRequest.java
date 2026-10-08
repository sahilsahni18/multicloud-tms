package com.trackflow.tms.dto.ticket;

import com.trackflow.tms.entity.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record TransitionRequest(@Schema(example = "IN_PROGRESS") @NotNull TicketStatus status) {
}
