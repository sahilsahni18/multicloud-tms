package com.trackflow.tms.dto.ticket;

import com.trackflow.tms.dto.common.UserRef;
import com.trackflow.tms.entity.HistoryChangeType;
import java.time.Instant;

public record HistoryResponse(
        Long id,
        HistoryChangeType changeType,
        String field,
        String oldValue,
        String newValue,
        UserRef changedBy,
        Instant changedAt) {
}
