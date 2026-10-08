package com.trackflow.tms.dto.ticket;

import io.swagger.v3.oas.annotations.media.Schema;

public record AssignTicketRequest(
        @Schema(description = "User to assign; null to unassign", example = "3") Long assigneeId) {
}
