package com.trackflow.tms.service;

import com.trackflow.tms.dto.activity.ActivityResponse;
import com.trackflow.tms.dto.dashboard.DashboardResponse;
import com.trackflow.tms.dto.dashboard.DashboardResponse.ProductivityRow;
import com.trackflow.tms.dto.dashboard.DashboardResponse.Scope;
import com.trackflow.tms.dto.dashboard.DashboardResponse.Totals;
import com.trackflow.tms.dto.dashboard.DashboardResponse.WeekCount;
import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.entity.Ticket;
import com.trackflow.tms.entity.TicketPriority;
import com.trackflow.tms.entity.TicketStatus;
import com.trackflow.tms.entity.TicketType;
import com.trackflow.tms.entity.User;
import com.trackflow.tms.exception.NotFoundException;
import com.trackflow.tms.repository.ProjectRepository;
import com.trackflow.tms.security.AuthUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard aggregates. Every number is computed with the same visibility
 * specification as the ticket search, so a role never sees counts for
 * tickets it cannot open.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    public static final int MAX_WEEKS = 12;
    private static final int RECENT_ACTIVITY = 10;

    private final EntityManager entityManager;
    private final TicketAccessPolicy policy;
    private final ProjectAccessPolicy projectAccess;
    private final ProjectRepository projectRepository;
    private final ActivityService activityService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(AuthUser user, Long projectId, int weeks) {
        int period = Math.max(1, Math.min(MAX_WEEKS, weeks));
        Scope scope = user.hasRole(RoleName.ADMIN) ? Scope.GLOBAL
                : user.hasRole(RoleName.PROJECT_MANAGER) ? Scope.PROJECTS
                : Scope.PERSONAL;

        Specification<Ticket> spec = policy.visibleTo(user);
        if (scope == Scope.PERSONAL) {
            String personalField = user.hasRole(RoleName.DEVELOPER) ? "assignee" : "reporter";
            spec = spec.and((root, query, cb) -> cb.equal(root.get(personalField).get("id"), user.getId()));
        }
        if (projectId != null) {
            projectRepository.findByIdAndDeletedAtIsNull(projectId)
                    .filter(p -> projectAccess.canView(user, p))
                    .orElseThrow(() -> NotFoundException.of("Project", projectId));
            spec = spec.and((root, query, cb) -> cb.equal(root.get("project").get("id"), projectId));
        }

        Map<TicketStatus, Long> byStatus = countBy(spec, "status", TicketStatus.class);
        Map<TicketPriority, Long> byPriority = countBy(spec, "priority", TicketPriority.class);
        Map<TicketType, Long> byType = countBy(spec, "type", TicketType.class);

        Instant now = clock.instant();
        Instant weekAgo = now.minus(Duration.ofDays(7));
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Specification<Ticket> notClosed = (root, query, cb) -> cb.notEqual(root.get("status"), TicketStatus.CLOSED);

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long closed = byStatus.get(TicketStatus.CLOSED);
        Totals totals = new Totals(
                total,
                total - closed,
                closed,
                count(spec.and(notClosed).and((root, query, cb) -> cb.isNull(root.get("assignee")))),
                count(spec.and(notClosed).and((root, query, cb) -> cb.lessThan(root.<LocalDate>get("dueDate"), today))),
                count(spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), weekAgo))),
                count(spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("closedAt"), weekAgo))));

        List<ProductivityRow> productivity = scope == Scope.PERSONAL ? List.of() : productivity(spec, period, now);
        List<ActivityResponse> recent = activityService.feed(user, projectId,
                PageRequest.of(0, RECENT_ACTIVITY, Sort.by(Sort.Direction.DESC, "createdAt", "id"))).content();

        return new DashboardResponse(scope, projectId, totals, byStatus, byPriority, byType, productivity, recent);
    }

    /** Per assignee: open workload, tickets closed per week over the period, average hours to close. */
    private List<ProductivityRow> productivity(Specification<Ticket> spec, int weeks, Instant now) {
        LocalDate thisWeek = LocalDate.ofInstant(now, ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstWeek = thisWeek.minusWeeks(weeks - 1L);
        Instant periodStart = firstWeek.atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<Long, Accumulator> rows = new LinkedHashMap<>();

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> openQuery = cb.createTupleQuery();
        Root<Ticket> openRoot = openQuery.from(Ticket.class);
        Join<Ticket, User> openAssignee = openRoot.join("assignee");
        openQuery.multiselect(openAssignee.get("id"), openAssignee.get("fullName"), cb.count(openRoot))
                .where(and(cb, spec.toPredicate(openRoot, openQuery, cb),
                        cb.notEqual(openRoot.get("status"), TicketStatus.CLOSED)))
                .groupBy(openAssignee.get("id"), openAssignee.get("fullName"));
        for (Tuple row : entityManager.createQuery(openQuery).getResultList()) {
            rows.computeIfAbsent(row.get(0, Long.class), id -> new Accumulator(row.get(1, String.class), firstWeek, weeks))
                    .openAssigned = row.get(2, Long.class);
        }

        CriteriaQuery<Tuple> closedQuery = cb.createTupleQuery();
        Root<Ticket> closedRoot = closedQuery.from(Ticket.class);
        Join<Ticket, User> closedAssignee = closedRoot.join("assignee");
        closedQuery.multiselect(closedAssignee.get("id"), closedAssignee.get("fullName"),
                        closedRoot.get("createdAt"), closedRoot.get("closedAt"))
                .where(and(cb, spec.toPredicate(closedRoot, closedQuery, cb),
                        cb.equal(closedRoot.get("status"), TicketStatus.CLOSED),
                        cb.greaterThanOrEqualTo(closedRoot.<Instant>get("closedAt"), periodStart)));
        for (Tuple row : entityManager.createQuery(closedQuery).getResultList()) {
            Accumulator acc = rows.computeIfAbsent(row.get(0, Long.class),
                    id -> new Accumulator(row.get(1, String.class), firstWeek, weeks));
            acc.addClosed(row.get(2, Instant.class), row.get(3, Instant.class));
        }

        List<ProductivityRow> result = new ArrayList<>();
        rows.forEach((userId, acc) -> result.add(acc.toRow(userId)));
        result.sort(Comparator.comparingLong(ProductivityRow::closedInPeriod).reversed()
                .thenComparing(ProductivityRow::fullName));
        return result;
    }

    private <E extends Enum<E>> Map<E, Long> countBy(Specification<Ticket> spec, String attribute, Class<E> type) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Ticket> root = query.from(Ticket.class);
        Path<E> path = root.get(attribute);
        query.multiselect(path, cb.count(root)).where(and(cb, spec.toPredicate(root, query, cb))).groupBy(path);

        Map<E, Long> result = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            result.put(value, 0L);
        }
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            result.put(row.get(0, type), row.get(1, Long.class));
        }
        return result;
    }

    private long count(Specification<Ticket> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Ticket> root = query.from(Ticket.class);
        query.select(cb.count(root)).where(and(cb, spec.toPredicate(root, query, cb)));
        return entityManager.createQuery(query).getSingleResult();
    }

    private static Predicate and(CriteriaBuilder cb, Predicate... predicates) {
        return cb.and(java.util.Arrays.stream(predicates).filter(java.util.Objects::nonNull).toArray(Predicate[]::new));
    }

    private static final class Accumulator {
        private final String fullName;
        private final LocalDate firstWeek;
        private final long[] perWeek;
        private long openAssigned;
        private long closed;
        private long totalMinutesToClose;

        Accumulator(String fullName, LocalDate firstWeek, int weeks) {
            this.fullName = fullName;
            this.firstWeek = firstWeek;
            this.perWeek = new long[weeks];
        }

        void addClosed(Instant createdAt, Instant closedAt) {
            LocalDate week = LocalDate.ofInstant(closedAt, ZoneOffset.UTC)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            int index = (int) java.time.temporal.ChronoUnit.WEEKS.between(firstWeek, week);
            if (index >= 0 && index < perWeek.length) {
                perWeek[index]++;
            }
            closed++;
            totalMinutesToClose += Math.max(0, Duration.between(createdAt, closedAt).toMinutes());
        }

        ProductivityRow toRow(Long userId) {
            List<WeekCount> weeks = new ArrayList<>();
            for (int i = 0; i < perWeek.length; i++) {
                weeks.add(new WeekCount(firstWeek.plusWeeks(i), perWeek[i]));
            }
            Double avgHours = closed == 0 ? null : Math.round(totalMinutesToClose / 60.0 / closed * 10) / 10.0;
            return new ProductivityRow(userId, fullName, openAssigned, closed, avgHours, weeks);
        }
    }
}
