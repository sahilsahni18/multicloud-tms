package com.trackflow.tms.dto.ticket;

import com.trackflow.tms.dto.common.UserRef;
import java.time.Instant;

/** One item of the merged ticket timeline: either a comment or a field change. */
public record TimelineEntryResponse(
        Kind kind,
        Instant at,
        UserRef actor,
        CommentResponse comment,
        HistoryResponse change) {

    public enum Kind { COMMENT, CHANGE }

    public static TimelineEntryResponse of(CommentResponse comment) {
        return new TimelineEntryResponse(Kind.COMMENT, comment.createdAt(), comment.author(), comment, null);
    }

    public static TimelineEntryResponse of(HistoryResponse change) {
        return new TimelineEntryResponse(Kind.CHANGE, change.changedAt(), change.changedBy(), null, change);
    }
}
