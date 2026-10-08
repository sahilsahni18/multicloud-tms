package com.trackflow.tms.dto.ticket;

import com.trackflow.tms.dto.common.UserRef;
import com.trackflow.tms.entity.HistoryChangeType;
import com.trackflow.tms.entity.TicketHistory;
import java.time.Instant;

public record HistoryResponse(
        Long id,
        HistoryChangeType changeType,
        String field,
        String oldValue,
        String newValue,
        UserRef changedBy,
        Instant changedAt) {

    public static HistoryResponse from(TicketHistory h) {
        return new HistoryResponse(h.getId(), h.getChangeType(), h.getFieldName(), h.getOldValue(),
                h.getNewValue(), UserRef.of(h.getChangedBy()), h.getChangedAt());
    }
}
