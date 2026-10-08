package com.trackflow.tms.controller;

import com.trackflow.tms.dto.common.PageResponse;
import com.trackflow.tms.dto.project.AddMemberRequest;
import com.trackflow.tms.dto.project.CreateProjectRequest;
import com.trackflow.tms.dto.project.MemberResponse;
import com.trackflow.tms.dto.project.ProjectResponse;
import com.trackflow.tms.dto.project.UpdateProjectRequest;
import com.trackflow.tms.security.AuthUser;
import com.trackflow.tms.service.ProjectService;
import com.trackflow.tms.util.SortGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Projects and their members")
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Projects I can see (admins: all) with member and ticket counts")
    public PageResponse<ProjectResponse> list(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(required = false) String q,
                                              @ParameterObject @PageableDefault(size = 50, sort = "name")
                                              Pageable pageable) {
        return projectService.list(user, q, SortGuard.require(pageable, Set.of("name", "projectKey", "createdAt")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a project")
    public ProjectResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return projectService.get(user, id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @Operation(summary = "Create a project; the creator (or ownerId, admin only) becomes owner and member")
    public ResponseEntity<ProjectResponse> create(@AuthenticationPrincipal AuthUser user,
                                                  @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse created = projectService.create(user, request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @Operation(summary = "Update name and description (admin or the owning project manager)")
    public ProjectResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                  @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(user, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a project (soft delete; its tickets disappear from lists)")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        projectService.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Project members, owner first")
    public List<MemberResponse> members(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return projectService.members(user, id);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @Operation(summary = "Add a member (admin or the owning project manager)")
    public List<MemberResponse> addMember(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                          @Valid @RequestBody AddMemberRequest request) {
        return projectService.addMember(user, id, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER')")
    @Operation(summary = "Remove a member (not the owner)")
    public List<MemberResponse> removeMember(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                             @PathVariable Long userId) {
        return projectService.removeMember(user, id, userId);
    }
}
