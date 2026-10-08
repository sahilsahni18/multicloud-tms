package com.trackflow.tms.service;

import com.trackflow.tms.dto.activity.ActivityResponse;
import com.trackflow.tms.dto.common.PageResponse;
import com.trackflow.tms.entity.ActivityLog;
import com.trackflow.tms.entity.HistoryChangeType;
import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.entity.Ticket;
import com.trackflow.tms.entity.TicketHistory;
import com.trackflow.tms.repository.ActivityLogRepository;
import com.trackflow.tms.repository.ProjectRepository;
import com.trackflow.tms.repository.TicketHistoryRepository;
import com.trackflow.tms.repository.UserRepository;
import com.trackflow.tms.security.AuthUser;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail: field-level ticket_history rows and the
 * activity_logs feed. Always called inside the caller's transaction, so a
 * change and its audit record commit or roll back together.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final int MAX_VALUE = 1000;
    private static final int MAX_SUMMARY = 500;

    private final TicketHistoryRepository historyRepository;
    private final ActivityLogRepository activityRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void ticketCreated(Ticket ticket, AuthUser actor) {
        history(ticket, actor, HistoryChangeType.CREATED, null, null, null);
        log(actor, ActivityActions.TICKET_CREATED, ActivityActions.ENTITY_TICKET, ticket.getId(),
                ticket.getProject().getId(), "Created " + ticket.getKey() + ": " + ticket.getTitle(),
                Map.of("type", ticket.getType().name(), "priority", ticket.getPriority().name()));
    }

    /** Records one changed field on a ticket. Values are rendered with toString(); null shows as "-". */
    @Transactional(propagation = Propagation.MANDATORY)
    public void ticketChanged(Ticket ticket, AuthUser actor, HistoryChangeType type, String field,
                              Object oldValue, Object newValue) {
        String oldText = render(oldValue);
        String newText = render(newValue);
        history(ticket, actor, type, field, oldText, newText);
        String action = switch (type) {
            case ASSIGNED -> ActivityActions.TICKET_ASSIGNED;
            case STATUS_CHANGED -> ActivityActions.STATUS_CHANGED;
            default -> ActivityActions.TICKET_UPDATED;
        };
        log(actor, action, ActivityActions.ENTITY_TICKET, ticket.getId(), ticket.getProject().getId(),
                ticket.getKey() + ": " + field + " " + display(oldText) + " -> " + display(newText), null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void log(AuthUser actor, String action, String entityType, Long entityId, Long projectId,
                    String summary, Map<String, Object> metadata) {
        ActivityLog entry = new ActivityLog();
        entry.setActor(actor == null ? null : userRepository.getReferenceById(actor.getId()));
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setProjectId(projectId);
        entry.setSummary(truncate(summary, MAX_SUMMARY));
        entry.setMetadata(metadata);
        entry.setCreatedAt(clock.instant());
        activityRepository.save(entry);
    }

    /**
     * Activity the caller may see: admins everything; project managers and
     * developers their projects; everyone their own actions.
     */
    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> feed(AuthUser user, Long projectId, Pageable pageable) {
        boolean admin = user.hasRole(RoleName.ADMIN);
        List<Long> projectIds = !admin && user.hasAnyRole(RoleName.PROJECT_MANAGER, RoleName.DEVELOPER)
                ? projectRepository.findAccessibleProjectIds(user.getId())
                : List.of();
        Specification<ActivityLog> visible = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (projectId != null) {
                predicates.add(cb.equal(root.get("projectId"), projectId));
            }
            if (!admin) {
                Predicate own = cb.equal(root.get("actor").get("id"), user.getId());
                predicates.add(projectIds.isEmpty() ? own : cb.or(own, root.get("projectId").in(projectIds)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(activityRepository.findAll(visible, pageable), ActivityResponse::from);
    }

    private void history(Ticket ticket, AuthUser actor, HistoryChangeType type, String field,
                         String oldValue, String newValue) {
        TicketHistory row = new TicketHistory();
        row.setTicket(ticket);
        row.setChangedBy(actor == null ? null : userRepository.getReferenceById(actor.getId()));
        row.setChangeType(type);
        row.setFieldName(field);
        row.setOldValue(truncate(oldValue, MAX_VALUE));
        row.setNewValue(truncate(newValue, MAX_VALUE));
        row.setChangedAt(clock.instant());
        historyRepository.save(row);
    }

    private static String render(Object value) {
        return value == null ? null : value.toString();
    }

    private static String display(String value) {
        return value == null ? "-" : value;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
