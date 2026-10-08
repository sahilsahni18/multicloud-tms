package com.trackflow.tms.dto.ticket;

import com.trackflow.tms.entity.TicketPriority;
import com.trackflow.tms.entity.TicketType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateTicketRequest(
        @Schema(example = "1") @NotNull Long projectId,
        @Schema(example = "Checkout page crashes on submit") @NotBlank @Size(max = 200) String title,
        @Size(max = 20000) String description,
        @Schema(description = "Defaults to TASK") TicketType type,
        @Schema(description = "Defaults to MEDIUM") TicketPriority priority,
        @Schema(description = "Admin / project manager only") Long assigneeId,
        LocalDate dueDate) {
}
