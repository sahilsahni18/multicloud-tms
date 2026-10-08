package com.trackflow.tms.dto.ticket;

import com.trackflow.tms.entity.TicketPriority;
import com.trackflow.tms.entity.TicketStatus;
import com.trackflow.tms.entity.TicketType;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;

/**
 * Search filters; every filter is optional and they combine with AND.
 * Repeating a list parameter means OR within it: ?status=OPEN&status=IN_PROGRESS.
 */
public record TicketFilter(
        @Parameter(description = "Project id") Long projectId,
        @Parameter(description = "One or more statuses") List<TicketStatus> status,
        @Parameter(description = "One or more priorities") List<TicketPriority> priority,
        @Parameter(description = "One or more types") List<TicketType> type,
        @Parameter(description = "Assignee user id") Long assigneeId,
        @Parameter(description = "true = only tickets without an assignee") Boolean unassigned,
        @Parameter(description = "Reporter user id") Long reporterId,
        @Parameter(description = "Text in the title, or an exact key such as TMS-4") String q) {
}
