package com.trackflow.tms.controller;

import com.trackflow.tms.dto.activity.ActivityResponse;
import com.trackflow.tms.dto.common.PageResponse;
import com.trackflow.tms.dto.dashboard.DashboardResponse;
import com.trackflow.tms.security.AuthUser;
import com.trackflow.tms.service.ActivityService;
import com.trackflow.tms.service.DashboardService;
import com.trackflow.tms.util.SortGuard;
import java.util.Set;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Dashboard & activity", description = "Metrics and the activity feed")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ActivityService activityService;

    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Totals, breakdowns, team productivity and recent activity for my scope",
            description = "Scope: GLOBAL for admins, PROJECTS for project managers, PERSONAL otherwise "
                    + "(developers: assigned to me; users: reported by me).")
    public DashboardResponse dashboard(@AuthenticationPrincipal AuthUser user,
                                       @RequestParam(required = false) Long projectId,
                                       @RequestParam(defaultValue = "4") int weeks) {
        return dashboardService.dashboard(user, projectId, weeks);
    }

    @GetMapping("/activity")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Activity feed: admins see everything, others their projects and own actions")
    public PageResponse<ActivityResponse> activity(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) Long projectId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return activityService.feed(user, projectId, SortGuard.require(pageable, Set.of("createdAt", "id")));
    }
}
