package com.trackflow.tms.mapper;

import com.trackflow.tms.dto.deployment.DeploymentDetailResponse;
import com.trackflow.tms.dto.deployment.DeploymentResponse;
import com.trackflow.tms.entity.Deployment;
import com.trackflow.tms.entity.DeploymentEvent;
import org.mapstruct.Mapper;

@Mapper(uses = UserMapper.class)
public interface DeploymentMapper {

    DeploymentResponse toResponse(Deployment deployment);

    DeploymentDetailResponse.EventResponse toEvent(DeploymentEvent event);
}
