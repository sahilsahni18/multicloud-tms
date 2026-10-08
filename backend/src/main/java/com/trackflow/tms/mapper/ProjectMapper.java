package com.trackflow.tms.mapper;

import com.trackflow.tms.dto.project.ProjectResponse;
import com.trackflow.tms.entity.Project;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Project -> API shape; the counts and the caller's rights come from separate queries. */
@Mapper(uses = UserMapper.class)
public interface ProjectMapper {

    @Mapping(target = "key", source = "project.projectKey")
    ProjectResponse toResponse(Project project, long memberCount, long totalTickets, long openTickets, boolean canManage);
}
