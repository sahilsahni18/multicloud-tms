package com.trackflow.tms.mapper;

import com.trackflow.tms.dto.common.UserRef;
import com.trackflow.tms.dto.ticket.CommentResponse;
import com.trackflow.tms.dto.ticket.TicketDetailResponse;
import com.trackflow.tms.entity.Comment;
import com.trackflow.tms.entity.Ticket;
import com.trackflow.tms.security.AuthUser;
import com.trackflow.tms.service.TicketAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Entity -> DTO for responses that depend on who is asking (permissions, allowed moves). */
@Component
@RequiredArgsConstructor
public class TicketMapper {

    private final TicketAccessPolicy policy;

    public TicketDetailResponse toDetail(Ticket t, AuthUser viewer) {
        boolean manager = policy.isManager(viewer, t);
        var permissions = new TicketDetailResponse.Permissions(
                manager || policy.canEdit(viewer, t),
                manager,
                manager,
                policy.canComment(viewer, t));
        return new TicketDetailResponse(t.getId(), t.getKey(), t.getTitle(), t.getDescription(), t.getType(),
                t.getPriority(), t.getStatus(), t.getProject().getId(), t.getProject().getProjectKey(),
                t.getProject().getName(), UserRef.of(t.getAssignee()), UserRef.of(t.getReporter()), t.getDueDate(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getClosedAt(), t.getVersion(),
                policy.allowedTransitions(viewer, t), permissions);
    }

    public CommentResponse toComment(Comment c, AuthUser viewer) {
        return new CommentResponse(c.getId(), c.getTicket().getId(), UserRef.of(c.getAuthor()), c.getBody(),
                c.getCreatedAt(), c.getEditedAt(), policy.canModifyComment(viewer, c));
    }
}
