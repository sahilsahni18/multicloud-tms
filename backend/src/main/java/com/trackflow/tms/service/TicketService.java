package com.trackflow.tms.service;

import com.trackflow.tms.dto.common.PageResponse;
import com.trackflow.tms.dto.ticket.AssignTicketRequest;
import com.trackflow.tms.dto.ticket.CreateTicketRequest;
import com.trackflow.tms.dto.ticket.HistoryResponse;
import com.trackflow.tms.dto.ticket.TicketDetailResponse;
import com.trackflow.tms.dto.ticket.TicketFilter;
import com.trackflow.tms.dto.ticket.TicketSummaryResponse;
import com.trackflow.tms.dto.ticket.TimelineEntryResponse;
import com.trackflow.tms.dto.ticket.TransitionRequest;
import com.trackflow.tms.dto.ticket.UpdateTicketRequest;
import com.trackflow.tms.entity.HistoryChangeType;
import com.trackflow.tms.entity.Project;
import com.trackflow.tms.entity.Ticket;
import com.trackflow.tms.entity.TicketPriority;
import com.trackflow.tms.entity.TicketStatus;
import com.trackflow.tms.entity.TicketType;
import com.trackflow.tms.entity.User;
import com.trackflow.tms.exception.BadRequestException;
import com.trackflow.tms.exception.ConflictException;
import com.trackflow.tms.exception.ForbiddenException;
import com.trackflow.tms.exception.NotFoundException;
import com.trackflow.tms.mapper.TicketMapper;
import com.trackflow.tms.repository.CommentRepository;
import com.trackflow.tms.repository.ProjectRepository;
import com.trackflow.tms.repository.TicketHistoryRepository;
import com.trackflow.tms.repository.TicketRepository;
import com.trackflow.tms.repository.UserRepository;
import com.trackflow.tms.security.AuthUser;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ticket lifecycle. Every method loads the ticket through the access policy
 * (tickets the caller cannot see are reported as 404, not 403), then checks
 * the specific permission (403), then the workflow (409).
 */
@Service
@RequiredArgsConstructor
public class TicketService {

    public static final int EXPORT_LIMIT = 5000;
    private static final Pattern TICKET_KEY = Pattern.compile("^([A-Z][A-Z0-9]{1,9})-(\\d{1,9})$");

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TicketHistoryRepository historyRepository;
    private final CommentRepository commentRepository;
    private final TicketAccessPolicy policy;
    private final ProjectAccessPolicy projectAccess;
    private final ActivityService activity;
    private final TicketMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<TicketSummaryResponse> search(AuthUser user, TicketFilter filter, Pageable pageable) {
        Page<Ticket> page = ticketRepository.findAll(
                policy.visibleTo(user)
                        .and(TicketSpecifications.matching(filter))
                        .and(TicketSpecifications.orderedBy(pageable.getSort())),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        return PageResponse.of(page, mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public List<TicketSummaryResponse> export(AuthUser user, TicketFilter filter, Pageable pageable) {
        return ticketRepository.findAll(
                        policy.visibleTo(user)
                                .and(TicketSpecifications.matching(filter))
                                .and(TicketSpecifications.orderedBy(pageable.getSort())),
                        PageRequest.of(0, EXPORT_LIMIT))
                .map(mapper::toSummary)
                .getContent();
    }

    @Transactional(readOnly = true)
    public TicketDetailResponse get(AuthUser user, Long id) {
        return mapper.toDetail(loadVisible(user, id), user);
    }

    @Transactional(readOnly = true)
    public TicketDetailResponse getByKey(AuthUser user, String key) {
        Matcher matcher = TICKET_KEY.matcher(key.toUpperCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new NotFoundException("Ticket " + key + " was not found");
        }
        Ticket ticket = ticketRepository.findActiveByKey(matcher.group(1), Integer.parseInt(matcher.group(2)))
                .filter(t -> policy.canView(user, t))
                .orElseThrow(() -> new NotFoundException("Ticket " + key + " was not found"));
        return mapper.toDetail(ticket, user);
    }

    @Transactional
    public TicketDetailResponse create(AuthUser user, CreateTicketRequest request) {
        Project project = projectRepository.findActiveByIdForUpdate(request.projectId())
                .filter(p -> projectAccess.canView(user, p))
                .orElseThrow(() -> NotFoundException.of("Project", request.projectId()));

        Ticket ticket = new Ticket();
        ticket.setProject(project);
        ticket.setTicketNumber(project.allocateTicketNumber());
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description());
        ticket.setType(request.type() != null ? request.type() : TicketType.TASK);
        ticket.setPriority(request.priority() != null ? request.priority() : TicketPriority.MEDIUM);
        ticket.setDueDate(request.dueDate());
        ticket.setReporter(userRepository.getReferenceById(user.getId()));
        if (request.assigneeId() != null) {
            if (!projectAccess.isManagerOf(user, project)) {
                throw new ForbiddenException("Only an admin or the project's manager can assign tickets");
            }
            ticket.setAssignee(loadAssignable(project, request.assigneeId()));
        }
        ticketRepository.save(ticket);

        activity.ticketCreated(ticket, user);
        if (ticket.getAssignee() != null) {
            activity.ticketChanged(ticket, user, HistoryChangeType.ASSIGNED, "assignee", null,
                    ticket.getAssignee().getFullName());
        }
        return mapper.toDetail(ticket, user);
    }

    @Transactional
    public TicketDetailResponse update(AuthUser user, Long id, UpdateTicketRequest request) {
        Ticket ticket = loadVisible(user, id);
        if (!policy.canEdit(user, ticket)) {
            throw new ForbiddenException("You cannot edit this ticket");
        }
        if (!Objects.equals(ticket.getVersion(), request.version())) {
            throw new ConflictException("This ticket was changed by someone else. Reload it and try again.");
        }

        String title = request.title().trim();
        if (!title.equals(ticket.getTitle())) {
            activity.ticketChanged(ticket, user, HistoryChangeType.UPDATED, "title", ticket.getTitle(), title);
            ticket.setTitle(title);
        }
        if (!Objects.equals(request.description(), ticket.getDescription())) {
            activity.ticketChanged(ticket, user, HistoryChangeType.UPDATED, "description", null, null);
            ticket.setDescription(request.description());
        }
        if (request.type() != ticket.getType()) {
            activity.ticketChanged(ticket, user, HistoryChangeType.UPDATED, "type", ticket.getType(), request.type());
            ticket.setType(request.type());
        }
        if (request.priority() != ticket.getPriority()) {
            activity.ticketChanged(ticket, user, HistoryChangeType.UPDATED, "priority", ticket.getPriority(),
                    request.priority());
            ticket.setPriority(request.priority());
        }
        if (!Objects.equals(request.dueDate(), ticket.getDueDate())) {
            activity.ticketChanged(ticket, user, HistoryChangeType.UPDATED, "dueDate", ticket.getDueDate(),
                    request.dueDate());
            ticket.setDueDate(request.dueDate());
        }
        ticketRepository.flush(); // bump the version now so the response carries it
        return mapper.toDetail(ticket, user);
    }

    @Transactional
    public TicketDetailResponse assign(AuthUser user, Long id, AssignTicketRequest request) {
        Ticket ticket = loadVisible(user, id);
        if (!policy.canAssign(user, ticket)) {
            throw new ForbiddenException("Only an admin or the project's manager can assign tickets");
        }
        User previous = ticket.getAssignee();
        User next = request.assigneeId() == null ? null : loadAssignable(ticket.getProject(), request.assigneeId());
        if (!Objects.equals(previous == null ? null : previous.getId(), next == null ? null : next.getId())) {
            ticket.setAssignee(next);
            activity.ticketChanged(ticket, user, HistoryChangeType.ASSIGNED, "assignee",
                    previous == null ? null : previous.getFullName(), next == null ? null : next.getFullName());
            ticketRepository.flush();
        }
        return mapper.toDetail(ticket, user);
    }

    @Transactional
    public TicketDetailResponse transition(AuthUser user, Long id, TransitionRequest request) {
        Ticket ticket = loadVisible(user, id);
        TicketStatus from = ticket.getStatus();
        TicketStatus to = request.status();
        if (from == to) {
            throw new ConflictException("Ticket " + ticket.getKey() + " is already " + to);
        }
        if (!from.canMoveTo(to)) {
            throw new ConflictException("Cannot move ticket from " + from + " to " + to
                    + ". Allowed from " + from + ": " + from.allowedNext());
        }
        if (!policy.canTransition(user, ticket, to)) {
            throw new ForbiddenException("You cannot move this ticket from " + from + " to " + to);
        }

        ticket.setStatus(to);
        ticket.setClosedAt(to == TicketStatus.CLOSED ? clock.instant() : null);
        activity.ticketChanged(ticket, user, HistoryChangeType.STATUS_CHANGED, "status", from, to);
        ticketRepository.flush();
        return mapper.toDetail(ticket, user);
    }

    @Transactional
    public void delete(AuthUser user, Long id) {
        Ticket ticket = loadVisible(user, id);
        if (!policy.canDelete(user, ticket)) {
            throw new ForbiddenException("Only an admin or the project's manager can delete tickets");
        }
        ticket.setDeletedAt(clock.instant());
        activity.log(user, ActivityActions.TICKET_DELETED, ActivityActions.ENTITY_TICKET, ticket.getId(),
                ticket.getProject().getId(), "Deleted " + ticket.getKey() + ": " + ticket.getTitle(), null);
    }

    @Transactional(readOnly = true)
    public List<HistoryResponse> history(AuthUser user, Long id) {
        Ticket ticket = loadVisible(user, id);
        return historyRepository.findByTicketId(ticket.getId()).stream().map(mapper::toHistory).toList();
    }

    /** Comments and field changes merged into one chronological list (oldest first). */
    @Transactional(readOnly = true)
    public List<TimelineEntryResponse> timeline(AuthUser user, Long id) {
        Ticket ticket = loadVisible(user, id);
        List<TimelineEntryResponse> entries = new ArrayList<>();
        historyRepository.findByTicketId(ticket.getId())
                .forEach(h -> entries.add(TimelineEntryResponse.of(mapper.toHistory(h))));
        commentRepository.findLiveByTicketId(ticket.getId())
                .forEach(c -> entries.add(TimelineEntryResponse.of(mapper.toComment(c, user))));
        entries.sort(Comparator.comparing(TimelineEntryResponse::at));
        return entries;
    }

    /** Loads a live ticket the user may see; anything else is a 404 so ticket ids do not leak. */
    Ticket loadVisible(AuthUser user, Long id) {
        return ticketRepository.findActiveById(id)
                .filter(t -> policy.canView(user, t))
                .orElseThrow(() -> NotFoundException.of("Ticket", id));
    }

    private User loadAssignable(Project project, Long userId) {
        User assignee = userRepository.findByIdAndDeletedAtIsNull(userId)
                .filter(User::isEnabled)
                .orElseThrow(() -> new BadRequestException("User " + userId + " does not exist or is disabled"));
        if (!project.isOwnedBy(userId) && !projectRepository.isMember(project.getId(), userId)) {
            throw new BadRequestException(assignee.getFullName() + " is not a member of project "
                    + project.getProjectKey());
        }
        return assignee;
    }
}
