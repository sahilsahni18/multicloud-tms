package com.trackflow.tms.dto.dashboard;

import com.trackflow.tms.dto.activity.ActivityResponse;
import com.trackflow.tms.entity.TicketPriority;
import com.trackflow.tms.entity.TicketStatus;
import com.trackflow.tms.entity.TicketType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Dashboard numbers for the caller's scope:
 * GLOBAL (admin), PROJECTS (project manager: their projects),
 * PERSONAL (developer: assigned to me; user: reported by me).
 */
public record DashboardResponse(
        Scope scope,
        Long projectId,
        Totals totals,
        Map<TicketStatus, Long> byStatus,
        Map<TicketPriority, Long> byPriority,
        Map<TicketType, Long> byType,
        List<ProductivityRow> productivity,
        List<ActivityResponse> recentActivity) {

    public enum Scope { GLOBAL, PROJECTS, PERSONAL }

    public record Totals(long total, long open, long closed, long unassigned, long overdue,
                         long createdLast7Days, long closedLast7Days) {
    }

    /** Per assignee: tickets closed in each of the last N weeks (Monday-based, UTC) and average time to close. */
    public record ProductivityRow(Long userId, String fullName, long openAssigned, long closedInPeriod,
                                  Double avgHoursToClose, List<WeekCount> closedPerWeek) {
    }

    public record WeekCount(LocalDate weekStart, long closed) {
    }
}
