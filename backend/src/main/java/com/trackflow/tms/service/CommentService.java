package com.trackflow.tms.service;

import com.trackflow.tms.dto.ticket.CommentRequest;
import com.trackflow.tms.dto.ticket.CommentResponse;
import com.trackflow.tms.entity.Comment;
import com.trackflow.tms.entity.Ticket;
import com.trackflow.tms.exception.ForbiddenException;
import com.trackflow.tms.exception.NotFoundException;
import com.trackflow.tms.mapper.TicketMapper;
import com.trackflow.tms.repository.CommentRepository;
import com.trackflow.tms.repository.UserRepository;
import com.trackflow.tms.security.AuthUser;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int SUMMARY_PREVIEW = 80;

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final TicketService ticketService;
    private final TicketAccessPolicy policy;
    private final ActivityService activity;
    private final TicketMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CommentResponse> list(AuthUser user, Long ticketId) {
        Ticket ticket = ticketService.loadVisible(user, ticketId);
        return commentRepository.findLiveByTicketId(ticket.getId()).stream()
                .map(c -> mapper.toComment(c, user))
                .toList();
    }

    @Transactional
    public CommentResponse add(AuthUser user, Long ticketId, CommentRequest request) {
        Ticket ticket = ticketService.loadVisible(user, ticketId);
        if (!policy.canComment(user, ticket)) {
            throw new ForbiddenException("You cannot comment on this ticket");
        }
        Comment comment = new Comment();
        comment.setTicket(ticket);
        comment.setAuthor(userRepository.getReferenceById(user.getId()));
        comment.setBody(request.body().trim());
        commentRepository.save(comment);
        activity.log(user, ActivityActions.COMMENT_ADDED, ActivityActions.ENTITY_COMMENT, comment.getId(),
                ticket.getProject().getId(), "Commented on " + ticket.getKey() + ": " + preview(comment.getBody()),
                Map.of("ticketId", ticket.getId()));
        commentRepository.flush();
        return mapper.toComment(comment, user);
    }

    @Transactional
    public CommentResponse edit(AuthUser user, Long commentId, CommentRequest request) {
        Comment comment = loadModifiable(user, commentId);
        comment.setBody(request.body().trim());
        comment.setEditedAt(clock.instant());
        activity.log(user, ActivityActions.COMMENT_EDITED, ActivityActions.ENTITY_COMMENT, comment.getId(),
                comment.getTicket().getProject().getId(), "Edited a comment on " + comment.getTicket().getKey(),
                Map.of("ticketId", comment.getTicket().getId()));
        commentRepository.flush();
        return mapper.toComment(comment, user);
    }

    @Transactional
    public void delete(AuthUser user, Long commentId) {
        Comment comment = loadModifiable(user, commentId);
        comment.setDeletedAt(clock.instant());
        activity.log(user, ActivityActions.COMMENT_DELETED, ActivityActions.ENTITY_COMMENT, comment.getId(),
                comment.getTicket().getProject().getId(), "Deleted a comment on " + comment.getTicket().getKey(),
                Map.of("ticketId", comment.getTicket().getId()));
    }

    private Comment loadModifiable(AuthUser user, Long commentId) {
        Comment comment = commentRepository.findLiveById(commentId)
                .filter(c -> c.getTicket().getDeletedAt() == null && policy.canView(user, c.getTicket()))
                .orElseThrow(() -> NotFoundException.of("Comment", commentId));
        if (!policy.canModifyComment(user, comment)) {
            throw new ForbiddenException("Only the author or an admin can change this comment");
        }
        return comment;
    }

    private static String preview(String body) {
        String singleLine = body.replaceAll("\\s+", " ");
        return singleLine.length() <= SUMMARY_PREVIEW ? singleLine : singleLine.substring(0, SUMMARY_PREVIEW) + "...";
    }
}
