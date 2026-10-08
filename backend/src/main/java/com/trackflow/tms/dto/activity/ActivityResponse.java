package com.trackflow.tms.dto.activity;

import com.trackflow.tms.dto.common.UserRef;
import com.trackflow.tms.entity.ActivityLog;
import java.time.Instant;

public record ActivityResponse(
        Long id,
        String action,
        String entityType,
        Long entityId,
        Long projectId,
        String summary,
        UserRef actor,
        Instant createdAt) {

    public static ActivityResponse from(ActivityLog a) {
        return new ActivityResponse(a.getId(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getProjectId(),
                a.getSummary(), UserRef.of(a.getActor()), a.getCreatedAt());
    }
}
