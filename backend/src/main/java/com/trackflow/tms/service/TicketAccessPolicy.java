package com.trackflow.tms.service;

import com.trackflow.tms.entity.Comment;
import com.trackflow.tms.entity.Project;
import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.entity.Ticket;
import com.trackflow.tms.entity.TicketStatus;
import com.trackflow.tms.entity.User;
import com.trackflow.tms.security.AuthUser;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Every row-level ticket rule in one place (PRD section 2):
 * <pre>
 * view       admin: all | PM, developer: tickets in their projects | anyone: reported or assigned to them
 * edit       admin, PM of the project | assignee | reporter while OPEN
 * assign     admin, PM of the project
 * delete     admin, PM of the project
 * status     admin, PM of the project: any legal move
 *            assignee: any legal move except closing and reopening
 *            reporter: reopen within 14 days of closing
 * comment    anyone who can view the ticket
 * edit/delete comment   admin, or the author
 * </pre>
 * {@link #visibleTo} is the query form of {@link #canView}; they must stay in sync.
 */
@Component
@RequiredArgsConstructor
public class TicketAccessPolicy {

    static final Duration REOPEN_WINDOW = Duration.ofDays(14);

    private final ProjectAccessPolicy projectAccess;
    private final Clock clock;

    public Specification<Ticket> visibleTo(AuthUser user) {
        return (root, query, cb) -> {
            Predicate live = cb.and(
                    cb.isNull(root.get("deletedAt")),
                    cb.isNull(root.get("project").get("deletedAt")));
            if (user.hasRole(RoleName.ADMIN)) {
                return live;
            }
            List<Predicate> anyOf = new ArrayList<>();
            anyOf.add(cb.equal(root.get("reporter").get("id"), user.getId()));
            anyOf.add(cb.equal(root.get("assignee").get("id"), user.getId()));
            if (user.hasAnyRole(RoleName.PROJECT_MANAGER, RoleName.DEVELOPER)) {
                Subquery<Long> myProjects = query.subquery(Long.class);
                Root<Project> project = myProjects.from(Project.class);
                Join<Project, User> member = project.join("members", JoinType.LEFT);
                myProjects.select(project.get("id")).where(cb.or(
                        cb.equal(project.get("owner").get("id"), user.getId()),
                        cb.equal(member.get("id"), user.getId())));
                anyOf.add(root.get("project").get("id").in(myProjects));
            }
            return cb.and(live, cb.or(anyOf.toArray(Predicate[]::new)));
        };
    }

    public boolean canView(AuthUser user, Ticket ticket) {
        if (user.hasRole(RoleName.ADMIN) || ticket.isReportedBy(user.getId()) || ticket.isAssignedTo(user.getId())) {
            return true;
        }
        return user.hasAnyRole(RoleName.PROJECT_MANAGER, RoleName.DEVELOPER)
                && projectAccess.isOwnerOrMember(user, ticket.getProject());
    }

    public boolean isManager(AuthUser user, Ticket ticket) {
        return projectAccess.isManagerOf(user, ticket.getProject());
    }

    public boolean canEdit(AuthUser user, Ticket ticket) {
        return isManager(user, ticket)
                || ticket.isAssignedTo(user.getId())
                || (ticket.isReportedBy(user.getId()) && ticket.getStatus() == TicketStatus.OPEN);
    }

    public boolean canAssign(AuthUser user, Ticket ticket) {
        return isManager(user, ticket);
    }

    public boolean canDelete(AuthUser user, Ticket ticket) {
        return isManager(user, ticket);
    }

    public boolean canComment(AuthUser user, Ticket ticket) {
        return canView(user, ticket);
    }

    /** Whether this user may make this move. Illegal moves are always false. */
    public boolean canTransition(AuthUser user, Ticket ticket, TicketStatus target) {
        TicketStatus from = ticket.getStatus();
        if (!from.canMoveTo(target)) {
            return false;
        }
        if (isManager(user, ticket)) {
            return true;
        }
        if (from == TicketStatus.CLOSED) {
            return ticket.isReportedBy(user.getId())
                    && ticket.getClosedAt() != null
                    && ticket.getClosedAt().plus(REOPEN_WINDOW).isAfter(clock.instant());
        }
        if (target == TicketStatus.CLOSED) {
            return false;
        }
        return ticket.isAssignedTo(user.getId());
    }

    public List<TicketStatus> allowedTransitions(AuthUser user, Ticket ticket) {
        return ticket.getStatus().allowedNext().stream()
                .filter(target -> canTransition(user, ticket, target))
                .sorted()
                .toList();
    }

    public boolean canModifyComment(AuthUser user, Comment comment) {
        return user.hasRole(RoleName.ADMIN) || comment.isWrittenBy(user.getId());
    }
}
