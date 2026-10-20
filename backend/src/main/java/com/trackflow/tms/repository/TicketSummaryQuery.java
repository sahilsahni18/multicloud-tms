package com.trackflow.tms.repository;

import com.trackflow.tms.dto.common.UserRef;
import com.trackflow.tms.dto.ticket.TicketSummaryResponse;
import com.trackflow.tms.entity.Project;
import com.trackflow.tms.entity.Ticket;
import com.trackflow.tms.entity.TicketPriority;
import com.trackflow.tms.entity.TicketStatus;
import com.trackflow.tms.entity.TicketType;
import com.trackflow.tms.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

/**
 * The ticket list as a projection: selects only the columns a list row shows,
 * instead of loading full Ticket, Project and two User entities (with the
 * description text and password hashes) and tracking them for dirty checking.
 * The count query only runs when the page does not already reveal the total.
 */
@Repository
@RequiredArgsConstructor
public class TicketSummaryQuery {

    private final EntityManager entityManager;

    public Page<TicketSummaryResponse> find(Specification<Ticket> filter, Specification<Ticket> order,
                                            Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Ticket> ticket = query.from(Ticket.class);
        Join<Ticket, Project> project = ticket.join("project");
        Join<Ticket, User> assignee = ticket.join("assignee", JoinType.LEFT);
        Join<Ticket, User> reporter = ticket.join("reporter");
        query.multiselect(
                ticket.get("id"), project.get("projectKey"), ticket.get("ticketNumber"), ticket.get("title"),
                ticket.get("type"), ticket.get("priority"), ticket.get("status"), project.get("id"),
                assignee.get("id"), assignee.get("fullName"), assignee.get("email"),
                reporter.get("id"), reporter.get("fullName"), reporter.get("email"),
                ticket.get("dueDate"), ticket.get("createdAt"), ticket.get("updatedAt"), ticket.get("closedAt"));
        Predicate where = filter.toPredicate(ticket, query, cb);
        if (where != null) {
            query.where(where);
        }
        order.toPredicate(ticket, query, cb); // sets ORDER BY

        List<TicketSummaryResponse> content = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList().stream()
                .map(TicketSummaryQuery::toSummary)
                .toList();
        return PageableExecutionUtils.getPage(content, pageable, () -> count(filter));
    }

    private long count(Specification<Ticket> filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Ticket> ticket = query.from(Ticket.class);
        Predicate where = filter.toPredicate(ticket, query, cb);
        query.select(cb.count(ticket));
        if (where != null) {
            query.where(where);
        }
        return entityManager.createQuery(query).getSingleResult();
    }

    private static TicketSummaryResponse toSummary(Tuple row) {
        Long assigneeId = row.get(8, Long.class);
        return new TicketSummaryResponse(
                row.get(0, Long.class),
                row.get(1, String.class) + "-" + row.get(2, Integer.class).intValue(),
                row.get(3, String.class),
                row.get(4, TicketType.class),
                row.get(5, TicketPriority.class),
                row.get(6, TicketStatus.class),
                row.get(7, Long.class),
                row.get(1, String.class),
                assigneeId == null ? null
                        : new UserRef(assigneeId, row.get(9, String.class), row.get(10, String.class)),
                new UserRef(row.get(11, Long.class), row.get(12, String.class), row.get(13, String.class)),
                row.get(14, LocalDate.class),
                row.get(15, Instant.class),
                row.get(16, Instant.class),
                row.get(17, Instant.class));
    }
}
