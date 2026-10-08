package com.trackflow.tms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.trackflow.tms.entity.Comment;
import com.trackflow.tms.entity.Project;
import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.entity.Ticket;
import com.trackflow.tms.entity.TicketStatus;
import com.trackflow.tms.entity.User;
import com.trackflow.tms.repository.ProjectRepository;
import com.trackflow.tms.security.AuthUser;
import com.trackflow.tms.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The permission matrix and workflow table from the PRD, rule by rule, without a database. */
class TicketAccessPolicyTest {

    private static final AuthUser ADMIN = user(1, RoleName.ADMIN);
    private static final AuthUser PM = user(2, RoleName.PROJECT_MANAGER);
    private static final AuthUser DEV = user(3, RoleName.DEVELOPER);
    private static final AuthUser OTHER_DEV = user(4, RoleName.DEVELOPER);
    private static final AuthUser REPORTER = user(5, RoleName.USER);
    private static final AuthUser STRANGER = user(6, RoleName.USER);
    private static final AuthUser OUTSIDE_PM = user(7, RoleName.PROJECT_MANAGER);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-08T10:00:00Z"));
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private TicketAccessPolicy policy;
    private Ticket ticket;

    private static AuthUser user(long id, RoleName role) {
        return AuthUser.fromToken(id, "u" + id + "@t.dev", "User " + id, Set.of(role));
    }

    private static User entity(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @BeforeEach
    void setUp() {
        policy = new TicketAccessPolicy(new ProjectAccessPolicy(projects), clock);
        Project project = new Project();
        project.setId(10L);
        project.setOwner(entity(2));                      // PM owns the project
        // Members: PM (owner), DEV, OTHER_DEV, REPORTER
        when(projects.isMember(any(), any())).thenAnswer(inv -> Set.of(2L, 3L, 4L, 5L).contains(inv.<Long>getArgument(1)));

        ticket = new Ticket();
        ticket.setProject(project);
        ticket.setReporter(entity(5));
        ticket.setAssignee(entity(3));
        ticket.setStatus(TicketStatus.OPEN);
    }

    @Test
    void visibility() {
        assertThat(policy.canView(ADMIN, ticket)).isTrue();
        assertThat(policy.canView(PM, ticket)).isTrue();
        assertThat(policy.canView(DEV, ticket)).isTrue();
        assertThat(policy.canView(OTHER_DEV, ticket)).isTrue();   // project member
        assertThat(policy.canView(REPORTER, ticket)).isTrue();
        assertThat(policy.canView(STRANGER, ticket)).isFalse();
        assertThat(policy.canView(OUTSIDE_PM, ticket)).isFalse();

        ticket.setReporter(entity(6));
        assertThat(policy.canView(REPORTER, ticket)).isFalse(); // USER role sees only own tickets, even as member
    }

    @Test
    void editing() {
        assertThat(policy.canEdit(PM, ticket)).isTrue();
        assertThat(policy.canEdit(DEV, ticket)).isTrue();         // assignee
        assertThat(policy.canEdit(OTHER_DEV, ticket)).isFalse();
        assertThat(policy.canEdit(REPORTER, ticket)).isTrue();    // reporter while OPEN
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        assertThat(policy.canEdit(REPORTER, ticket)).isFalse();
    }

    @Test
    void assigningAndDeletingIsForManagersOfTheProject() {
        assertThat(policy.canAssign(ADMIN, ticket)).isTrue();
        assertThat(policy.canAssign(PM, ticket)).isTrue();
        assertThat(policy.canAssign(OUTSIDE_PM, ticket)).isFalse();
        assertThat(policy.canAssign(DEV, ticket)).isFalse();
        assertThat(policy.canDelete(REPORTER, ticket)).isFalse();
    }

    @ParameterizedTest(name = "{0}: {1} -> {2} = {3}")
    @CsvSource({
            // who,      from,        to,          allowed
            "ASSIGNEE,  OPEN,        IN_PROGRESS, true",
            "ASSIGNEE,  OPEN,        CLOSED,      false",
            "ASSIGNEE,  IN_PROGRESS, IN_REVIEW,   true",
            "ASSIGNEE,  IN_PROGRESS, OPEN,        true",
            "ASSIGNEE,  IN_REVIEW,   IN_PROGRESS, true",
            "ASSIGNEE,  IN_REVIEW,   CLOSED,      false",
            "ASSIGNEE,  CLOSED,      OPEN,        false",
            "PM,        OPEN,        CLOSED,      true",
            "PM,        IN_REVIEW,   CLOSED,      true",
            "PM,        CLOSED,      OPEN,        true",
            "PM,        OPEN,        IN_REVIEW,   false",
            "OTHER_DEV, OPEN,        IN_PROGRESS, false",
            "REPORTER,  OPEN,        IN_PROGRESS, false",
            "REPORTER,  CLOSED,      OPEN,        true",
            "ADMIN,     IN_PROGRESS, IN_REVIEW,   true",
            "ADMIN,     CLOSED,      IN_REVIEW,   false",
    })
    void workflowPermissions(String who, TicketStatus from, TicketStatus to, boolean allowed) {
        AuthUser actor = switch (who) {
            case "ASSIGNEE" -> DEV;
            case "PM" -> PM;
            case "OTHER_DEV" -> OTHER_DEV;
            case "REPORTER" -> REPORTER;
            default -> ADMIN;
        };
        ticket.setStatus(from);
        if (from == TicketStatus.CLOSED) {
            ticket.setClosedAt(clock.instant().minus(Duration.ofDays(1)));
        }
        assertThat(policy.canTransition(actor, ticket, to)).isEqualTo(allowed);
    }

    @Test
    void reporterReopenWindowIs14Days() {
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setClosedAt(clock.instant());
        clock.advance(Duration.ofDays(13));
        assertThat(policy.canTransition(REPORTER, ticket, TicketStatus.OPEN)).isTrue();
        clock.advance(Duration.ofDays(2));
        assertThat(policy.canTransition(REPORTER, ticket, TicketStatus.OPEN)).isFalse();
        assertThat(policy.canTransition(PM, ticket, TicketStatus.OPEN)).isTrue();
    }

    @Test
    void allowedTransitionsAreFilteredForTheCaller() {
        ticket.setStatus(TicketStatus.IN_REVIEW);
        assertThat(policy.allowedTransitions(PM, ticket)).containsExactly(TicketStatus.IN_PROGRESS, TicketStatus.CLOSED);
        assertThat(policy.allowedTransitions(DEV, ticket)).containsExactly(TicketStatus.IN_PROGRESS);
        assertThat(policy.allowedTransitions(REPORTER, ticket)).isEmpty();
    }

    @Test
    void commentsBelongToTheirAuthor() {
        Comment comment = new Comment();
        comment.setAuthor(entity(3));
        assertThat(policy.canModifyComment(DEV, comment)).isTrue();
        assertThat(policy.canModifyComment(ADMIN, comment)).isTrue();
        assertThat(policy.canModifyComment(PM, comment)).isFalse();
        assertThat(policy.canComment(REPORTER, ticket)).isTrue();
        assertThat(policy.canComment(STRANGER, ticket)).isFalse();
    }
}
