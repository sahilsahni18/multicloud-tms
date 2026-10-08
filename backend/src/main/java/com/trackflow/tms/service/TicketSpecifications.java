package com.trackflow.tms.service;

import com.trackflow.tms.dto.ticket.TicketFilter;
import com.trackflow.tms.entity.Ticket;
import com.trackflow.tms.entity.TicketPriority;
import com.trackflow.tms.exception.BadRequestException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Search filters and sorting for tickets, as JPA Specifications. */
public final class TicketSpecifications {

    private static final Pattern TICKET_KEY = Pattern.compile("^([A-Z][A-Z0-9]{1,9})-(\\d{1,9})$");

    /** API sort name -> entity attribute. "priority" is handled separately (severity order, not alphabetical). */
    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "dueDate", "dueDate",
            "closedAt", "closedAt",
            "title", "title",
            "status", "status",
            "type", "type",
            "key", "ticketNumber");

    private TicketSpecifications() {
    }

    public static Specification<Ticket> matching(TicketFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.projectId() != null) {
                predicates.add(cb.equal(root.get("project").get("id"), filter.projectId()));
            }
            if (filter.status() != null && !filter.status().isEmpty()) {
                predicates.add(root.get("status").in(filter.status()));
            }
            if (filter.priority() != null && !filter.priority().isEmpty()) {
                predicates.add(root.get("priority").in(filter.priority()));
            }
            if (filter.type() != null && !filter.type().isEmpty()) {
                predicates.add(root.get("type").in(filter.type()));
            }
            if (Boolean.TRUE.equals(filter.unassigned())) {
                predicates.add(cb.isNull(root.get("assignee")));
            } else if (filter.assigneeId() != null) {
                predicates.add(cb.equal(root.get("assignee").get("id"), filter.assigneeId()));
            }
            if (filter.reporterId() != null) {
                predicates.add(cb.equal(root.get("reporter").get("id"), filter.reporterId()));
            }
            if (StringUtils.hasText(filter.q())) {
                predicates.add(textMatch(root, cb, filter.q().trim()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Applies the requested sort inside the query (so "priority" can sort by
     * severity) with id as a tie-breaker for stable paging. Skipped for the count query.
     */
    public static Specification<Ticket> orderedBy(Sort sort) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            if (!order.getProperty().equals("priority") && !SORTABLE.containsKey(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty() + "'. Allowed: priority, "
                        + String.join(", ", SORTABLE.keySet()));
            }
            orders.add(order);
        }
        if (orders.isEmpty()) {
            orders.add(Sort.Order.desc("createdAt"));
        }
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                List<Order> jpaOrders = new ArrayList<>();
                for (Sort.Order order : orders) {
                    Expression<?> expression = order.getProperty().equals("priority")
                            ? prioritySeverity(root, cb)
                            : root.get(SORTABLE.get(order.getProperty()));
                    jpaOrders.add(order.isAscending() ? cb.asc(expression) : cb.desc(expression));
                }
                jpaOrders.add(cb.desc(root.get("id")));
                query.orderBy(jpaOrders);
            }
            return null;
        };
    }

    private static Predicate textMatch(Root<Ticket> root, CriteriaBuilder cb, String text) {
        Predicate title = cb.like(root.get("title"), "%" + escapeLike(text) + "%", '\\');
        Matcher key = TICKET_KEY.matcher(text.toUpperCase(Locale.ROOT));
        if (!key.matches()) {
            return title;
        }
        return cb.or(title, cb.and(
                cb.equal(root.get("project").get("projectKey"), key.group(1)),
                cb.equal(root.get("ticketNumber"), Integer.parseInt(key.group(2)))));
    }

    private static Expression<Integer> prioritySeverity(Root<Ticket> root, CriteriaBuilder cb) {
        CriteriaBuilder.Case<Integer> severity = cb.selectCase();
        for (TicketPriority priority : TicketPriority.values()) {
            severity = severity.when(cb.equal(root.get("priority"), priority), priority.ordinal());
        }
        return severity.otherwise(0);
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
