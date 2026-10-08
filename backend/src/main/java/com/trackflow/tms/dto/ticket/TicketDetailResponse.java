package com.trackflow.tms.dto.ticket;

import com.trackflow.tms.dto.common.UserRef;
import com.trackflow.tms.entity.TicketPriority;
import com.trackflow.tms.entity.TicketStatus;
import com.trackflow.tms.entity.TicketType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Full ticket for the detail page. {@code allowedTransitions} and
 * {@code permissions} are computed for the caller, so the UI shows only the
 * buttons the API will accept.
 */
public record TicketDetailResponse(
        Long id,
        String key,
        String title,
        String description,
        TicketType type,
        TicketPriority priority,
        TicketStatus status,
        Long projectId,
        String projectKey,
        String projectName,
        UserRef assignee,
        UserRef reporter,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt,
        Long version,
        List<TicketStatus> allowedTransitions,
        Permissions permissions) {

    public record Permissions(boolean canEdit, boolean canAssign, boolean canDelete, boolean canComment) {
    }
}
