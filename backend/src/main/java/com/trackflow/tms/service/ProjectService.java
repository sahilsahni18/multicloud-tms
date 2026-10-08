package com.trackflow.tms.service;

import com.trackflow.tms.dto.common.PageResponse;
import com.trackflow.tms.dto.common.UserRef;
import com.trackflow.tms.dto.project.AddMemberRequest;
import com.trackflow.tms.dto.project.CreateProjectRequest;
import com.trackflow.tms.dto.project.MemberResponse;
import com.trackflow.tms.dto.project.ProjectResponse;
import com.trackflow.tms.dto.project.UpdateProjectRequest;
import com.trackflow.tms.entity.Project;
import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.entity.TicketStatus;
import com.trackflow.tms.entity.User;
import com.trackflow.tms.exception.BadRequestException;
import com.trackflow.tms.exception.ConflictException;
import com.trackflow.tms.exception.ForbiddenException;
import com.trackflow.tms.exception.NotFoundException;
import com.trackflow.tms.repository.ProjectRepository;
import com.trackflow.tms.repository.UserRepository;
import com.trackflow.tms.security.AuthUser;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectAccessPolicy access;
    private final ActivityService activity;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> list(AuthUser user, String q, Pageable pageable) {
        Page<Project> page = projectRepository.findVisible(user.hasRole(RoleName.ADMIN), user.getId(),
                StringUtils.hasText(q) ? q.trim() : null, pageable);
        List<Long> ids = page.getContent().stream().map(Project::getId).toList();
        Map<Long, Long> members = new HashMap<>();
        Map<Long, long[]> tickets = new HashMap<>();
        if (!ids.isEmpty()) {
            projectRepository.countMembers(ids).forEach(row -> members.put((Long) row[0], (Long) row[1]));
            projectRepository.countTickets(ids, TicketStatus.CLOSED).forEach(row -> tickets.put((Long) row[0],
                    new long[]{(Long) row[1], row[2] == null ? 0 : ((Number) row[2]).longValue()}));
        }
        return PageResponse.of(page, p -> toResponse(user, p, members.getOrDefault(p.getId(), 0L),
                tickets.getOrDefault(p.getId(), new long[]{0, 0})));
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(AuthUser user, Long id) {
        return toResponse(user, loadVisible(user, id));
    }

    @Transactional
    public ProjectResponse create(AuthUser user, CreateProjectRequest request) {
        String key = request.key().toUpperCase(Locale.ROOT);
        if (projectRepository.existsByProjectKey(key)) {
            throw new ConflictException("Project key " + key + " is already in use");
        }
        User owner = resolveOwner(user, request.ownerId());

        Project project = new Project();
        project.setProjectKey(key);
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setOwner(owner);
        project.getMembers().add(owner);
        projectRepository.save(project);
        activity.log(user, ActivityActions.PROJECT_CREATED, ActivityActions.ENTITY_PROJECT, project.getId(),
                project.getId(), "Created project " + key + " - " + project.getName(), null);
        return toResponse(user, project, 1, new long[]{0, 0});
    }

    @Transactional
    public ProjectResponse update(AuthUser user, Long id, UpdateProjectRequest request) {
        Project project = loadManageable(user, id);
        project.setName(request.name().trim());
        project.setDescription(request.description());
        activity.log(user, ActivityActions.PROJECT_UPDATED, ActivityActions.ENTITY_PROJECT, project.getId(),
                project.getId(), "Updated project " + project.getProjectKey(), null);
        return toResponse(user, project);
    }

    /** Soft delete: the project and its tickets disappear from every list. Admin only. */
    @Transactional
    public void delete(AuthUser user, Long id) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Project", id));
        project.setDeletedAt(clock.instant());
        activity.log(user, ActivityActions.PROJECT_DELETED, ActivityActions.ENTITY_PROJECT, project.getId(),
                project.getId(), "Deleted project " + project.getProjectKey(), null);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> members(AuthUser user, Long id) {
        return toMembers(loadVisible(user, id));
    }

    @Transactional
    public List<MemberResponse> addMember(AuthUser user, Long id, AddMemberRequest request) {
        Project project = loadManageable(user, id);
        User member = userRepository.findByIdAndDeletedAtIsNull(request.userId())
                .filter(User::isEnabled)
                .orElseThrow(() -> new BadRequestException("User " + request.userId() + " does not exist or is disabled"));
        if (project.getMembers().add(member)) {
            activity.log(user, ActivityActions.MEMBER_ADDED, ActivityActions.ENTITY_PROJECT, project.getId(),
                    project.getId(), "Added " + member.getFullName() + " to " + project.getProjectKey(), null);
        }
        return toMembers(project);
    }

    @Transactional
    public List<MemberResponse> removeMember(AuthUser user, Long id, Long userId) {
        Project project = loadManageable(user, id);
        if (project.isOwnedBy(userId)) {
            throw new BadRequestException("The project owner cannot be removed");
        }
        User removed = project.getMembers().stream().filter(m -> m.getId().equals(userId)).findFirst()
                .orElseThrow(() -> new NotFoundException("User " + userId + " is not a member of this project"));
        project.getMembers().remove(removed);
        activity.log(user, ActivityActions.MEMBER_REMOVED, ActivityActions.ENTITY_PROJECT, project.getId(),
                project.getId(), "Removed " + removed.getFullName() + " from " + project.getProjectKey(), null);
        return toMembers(project);
    }

    private Project loadVisible(AuthUser user, Long id) {
        return projectRepository.findByIdAndDeletedAtIsNull(id)
                .filter(p -> access.canView(user, p))
                .orElseThrow(() -> NotFoundException.of("Project", id));
    }

    private Project loadManageable(AuthUser user, Long id) {
        Project project = loadVisible(user, id);
        if (!access.canManage(user, project)) {
            throw new ForbiddenException("Only an admin or the project's owner can change this project");
        }
        return project;
    }

    /** Admins may create a project for another manager; everyone else owns what they create. */
    private User resolveOwner(AuthUser user, Long ownerId) {
        if (ownerId == null || ownerId.equals(user.getId())) {
            return userRepository.getReferenceById(user.getId());
        }
        if (!user.hasRole(RoleName.ADMIN)) {
            throw new ForbiddenException("Only an admin can create a project for someone else");
        }
        User owner = userRepository.findByIdAndDeletedAtIsNull(ownerId)
                .filter(User::isEnabled)
                .orElseThrow(() -> new BadRequestException("User " + ownerId + " does not exist or is disabled"));
        if (!owner.roleNames().contains(RoleName.PROJECT_MANAGER) && !owner.roleNames().contains(RoleName.ADMIN)) {
            throw new BadRequestException("The owner must be a project manager or an admin");
        }
        return owner;
    }

    private ProjectResponse toResponse(AuthUser user, Project project) {
        long members = projectRepository.countMembers(List.of(project.getId())).stream()
                .mapToLong(row -> (Long) row[1]).sum();
        long[] tickets = projectRepository.countTickets(List.of(project.getId()), TicketStatus.CLOSED).stream()
                .findFirst()
                .map(row -> new long[]{(Long) row[1], row[2] == null ? 0 : ((Number) row[2]).longValue()})
                .orElse(new long[]{0, 0});
        return toResponse(user, project, members, tickets);
    }

    private ProjectResponse toResponse(AuthUser user, Project p, long memberCount, long[] tickets) {
        return new ProjectResponse(p.getId(), p.getProjectKey(), p.getName(), p.getDescription(),
                UserRef.of(p.getOwner()), memberCount, tickets[0], tickets[1], p.getCreatedAt(),
                access.canManage(user, p));
    }

    private List<MemberResponse> toMembers(Project project) {
        return project.getMembers().stream()
                .filter(m -> m.getDeletedAt() == null)
                .sorted(Comparator.comparing((User m) -> !project.isOwnedBy(m.getId())).thenComparing(User::getFullName))
                .map(m -> new MemberResponse(m.getId(), m.getFullName(), m.getEmail(),
                        m.roleNames().stream().sorted().toList(), project.isOwnedBy(m.getId())))
                .toList();
    }
}
