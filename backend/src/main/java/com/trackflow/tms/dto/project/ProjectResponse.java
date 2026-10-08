package com.trackflow.tms.dto.project;

import com.trackflow.tms.dto.common.UserRef;
import java.time.Instant;

public record ProjectResponse(
        Long id,
        String key,
        String name,
        String description,
        UserRef owner,
        long memberCount,
        long totalTickets,
        long openTickets,
        Instant createdAt,
        boolean canManage) {
}
