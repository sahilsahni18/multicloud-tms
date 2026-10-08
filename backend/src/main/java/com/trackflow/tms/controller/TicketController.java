package com.trackflow.tms.controller;

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
import com.trackflow.tms.security.AuthUser;
import com.trackflow.tms.service.TicketService;
import com.trackflow.tms.util.CsvWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "Search, create, edit, assign, move through the workflow, history")
public class TicketController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv");

    private final TicketService ticketService;
    private final Clock clock;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search tickets I can see",
            description = "Filters combine with AND. Sort by createdAt, updatedAt, dueDate, closedAt, title, status, "
                    + "type, key or priority (severity order), e.g. sort=priority,desc. Max page size 100.")
    public PageResponse<TicketSummaryResponse> search(
            @AuthenticationPrincipal AuthUser user,
            @ParameterObject TicketFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ticketService.search(user, filter, pageable);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @Operation(summary = "Reports: the filtered ticket list as CSV (max 5000 rows)")
    public ResponseEntity<String> export(
            @AuthenticationPrincipal AuthUser user,
            @ParameterObject TicketFilter filter,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        List<TicketSummaryResponse> rows = ticketService.export(user, filter, pageable);
        CsvWriter csv = new CsvWriter("Key", "Title", "Type", "Priority", "Status", "Project", "Assignee",
                "Reporter", "Due date", "Created", "Closed");
        rows.forEach(t -> csv.row(t.key(), t.title(), t.type(), t.priority(), t.status(), t.projectKey(),
                t.assignee() == null ? "" : t.assignee().fullName(), t.reporter().fullName(), t.dueDate(),
                t.createdAt(), t.closedAt()));
        String filename = "tickets-" + LocalDate.now(clock.withZone(ZoneOffset.UTC)) + ".csv";
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv.toString());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ticket detail with the moves and actions allowed for me")
    public TicketDetailResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ticketService.get(user, id);
    }

    @GetMapping("/key/{key}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ticket detail by key, e.g. TMS-4")
    public TicketDetailResponse getByKey(@AuthenticationPrincipal AuthUser user, @PathVariable String key) {
        return ticketService.getByKey(user, key);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a ticket in a project I belong to")
    public ResponseEntity<TicketDetailResponse> create(@AuthenticationPrincipal AuthUser user,
                                                       @Valid @RequestBody CreateTicketRequest request) {
        TicketDetailResponse created = ticketService.create(user, request);
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Edit title, description, type, priority, due date (send the version you loaded)")
    public TicketDetailResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                       @Valid @RequestBody UpdateTicketRequest request) {
        return ticketService.update(user, id, request);
    }

    @PutMapping("/{id}/assignee")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @Operation(summary = "Assign to a project member, or unassign with null")
    public TicketDetailResponse assign(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                       @Valid @RequestBody AssignTicketRequest request) {
        return ticketService.assign(user, id, request);
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Move through the workflow (close = CLOSED, reopen = OPEN from CLOSED)",
            description = "409 if the move is not part of the workflow, 403 if it is not allowed for me")
    public TicketDetailResponse transition(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                           @Valid @RequestBody TransitionRequest request) {
        return ticketService.transition(user, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @Operation(summary = "Delete a ticket (soft delete)")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        ticketService.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Field-level change history (who changed what, when)")
    public List<HistoryResponse> history(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ticketService.history(user, id);
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Comments and changes merged in time order")
    public List<TimelineEntryResponse> timeline(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ticketService.timeline(user, id);
    }
}
