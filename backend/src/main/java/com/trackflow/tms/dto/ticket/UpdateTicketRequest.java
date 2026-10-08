package com.trackflow.tms.dto.ticket;

import com.trackflow.tms.entity.TicketPriority;
import com.trackflow.tms.entity.TicketType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Replaces the editable fields. {@code version} must match the ticket (optimistic locking). */
public record UpdateTicketRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 20000) String description,
        @NotNull TicketType type,
        @NotNull TicketPriority priority,
        LocalDate dueDate,
        @Schema(description = "The version you loaded; 409 if someone saved in between") @NotNull Long version) {
}
