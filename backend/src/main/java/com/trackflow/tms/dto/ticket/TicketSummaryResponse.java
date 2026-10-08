package com.trackflow.tms.dto.ticket;

import com.trackflow.tms.dto.common.UserRef;
import com.trackflow.tms.entity.Ticket;
import com.trackflow.tms.entity.TicketPriority;
import com.trackflow.tms.entity.TicketStatus;
import com.trackflow.tms.entity.TicketType;
import java.time.Instant;
import java.time.LocalDate;

/** A row in the ticket list. */
public record TicketSummaryResponse(
        Long id,
        String key,
        String title,
        TicketType type,
        TicketPriority priority,
        TicketStatus status,
        Long projectId,
        String projectKey,
        UserRef assignee,
        UserRef reporter,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt) {

    public static TicketSummaryResponse from(Ticket t) {
        return new TicketSummaryResponse(t.getId(), t.getKey(), t.getTitle(), t.getType(), t.getPriority(),
                t.getStatus(), t.getProject().getId(), t.getProject().getProjectKey(), UserRef.of(t.getAssignee()),
                UserRef.of(t.getReporter()), t.getDueDate(), t.getCreatedAt(), t.getUpdatedAt(), t.getClosedAt());
    }
}
