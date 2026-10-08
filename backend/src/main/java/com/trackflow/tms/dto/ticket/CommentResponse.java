package com.trackflow.tms.dto.ticket;

import com.trackflow.tms.dto.common.UserRef;
import java.time.Instant;

public record CommentResponse(
        Long id,
        Long ticketId,
        UserRef author,
        String body,
        Instant createdAt,
        Instant editedAt,
        boolean canModify) {
}
