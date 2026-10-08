package com.trackflow.tms.dto.activity;

import com.trackflow.tms.dto.common.UserRef;
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
}
