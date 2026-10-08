package com.trackflow.tms.mapper;

import com.trackflow.tms.dto.ticket.CommentResponse;
import com.trackflow.tms.dto.ticket.HistoryResponse;
import com.trackflow.tms.dto.ticket.TicketDetailResponse;
import com.trackflow.tms.dto.ticket.TicketSummaryResponse;
import com.trackflow.tms.entity.Comment;
import com.trackflow.tms.entity.Ticket;
import com.trackflow.tms.entity.TicketHistory;
import com.trackflow.tms.security.AuthUser;
import com.trackflow.tms.service.TicketAccessPolicy;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Ticket, comment and history -> API shapes. Detail and comment responses
 * depend on who is asking (allowed moves, permissions), so they take the
 * viewer as a {@link Context} parameter and ask {@link TicketAccessPolicy}.
 */
@Mapper(uses = UserMapper.class)
public abstract class TicketMapper {

    @Autowired
    protected TicketAccessPolicy policy;

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectKey", source = "project.projectKey")
    public abstract TicketSummaryResponse toSummary(Ticket ticket);

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectKey", source = "project.projectKey")
    @Mapping(target = "projectName", source = "project.name")
    @Mapping(target = "allowedTransitions", expression = "java(policy.allowedTransitions(viewer, ticket))")
    @Mapping(target = "permissions", expression = "java(permissions(ticket, viewer))")
    public abstract TicketDetailResponse toDetail(Ticket ticket, @Context AuthUser viewer);

    @Mapping(target = "ticketId", source = "ticket.id")
    @Mapping(target = "canModify", expression = "java(policy.canModifyComment(viewer, comment))")
    public abstract CommentResponse toComment(Comment comment, @Context AuthUser viewer);

    @Mapping(target = "field", source = "fieldName")
    public abstract HistoryResponse toHistory(TicketHistory history);

    protected TicketDetailResponse.Permissions permissions(Ticket ticket, AuthUser viewer) {
        boolean manager = policy.isManager(viewer, ticket);
        return new TicketDetailResponse.Permissions(
                manager || policy.canEdit(viewer, ticket),
                manager,
                manager,
                policy.canComment(viewer, ticket));
    }
}
