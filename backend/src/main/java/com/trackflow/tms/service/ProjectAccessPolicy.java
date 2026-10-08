package com.trackflow.tms.service;

import com.trackflow.tms.entity.Project;
import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.repository.ProjectRepository;
import com.trackflow.tms.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Row-level project rules from the PRD permission matrix:
 * <ul>
 *   <li>view: admin, owner, member</li>
 *   <li>update / manage members: admin, or a project manager who owns it</li>
 *   <li>assign tickets: admin, or a project manager who owns or belongs to it</li>
 * </ul>
 * Role-only rules (who may create or delete projects) are @PreAuthorize on the controller.
 */
@Component
@RequiredArgsConstructor
public class ProjectAccessPolicy {

    private final ProjectRepository projectRepository;

    public boolean isOwnerOrMember(AuthUser user, Project project) {
        return project.isOwnedBy(user.getId()) || projectRepository.isMember(project.getId(), user.getId());
    }

    public boolean canView(AuthUser user, Project project) {
        return user.hasRole(RoleName.ADMIN) || isOwnerOrMember(user, project);
    }

    public boolean canManage(AuthUser user, Project project) {
        return user.hasRole(RoleName.ADMIN)
                || (user.hasRole(RoleName.PROJECT_MANAGER) && project.isOwnedBy(user.getId()));
    }

    /** Admin, or a project manager working on this project: may assign, close, delete its tickets. */
    public boolean isManagerOf(AuthUser user, Project project) {
        return user.hasRole(RoleName.ADMIN)
                || (user.hasRole(RoleName.PROJECT_MANAGER) && isOwnerOrMember(user, project));
    }
}
