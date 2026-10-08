package com.trackflow.tms.mapper;

import com.trackflow.tms.dto.activity.ActivityResponse;
import com.trackflow.tms.entity.ActivityLog;
import org.mapstruct.Mapper;

@Mapper(uses = UserMapper.class)
public interface ActivityMapper {

    ActivityResponse toResponse(ActivityLog activity);
}
