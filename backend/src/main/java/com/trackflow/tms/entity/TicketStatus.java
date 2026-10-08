package com.trackflow.tms.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ticket workflow. Legal moves (who may make them is decided by
 * {@link com.trackflow.tms.service.TicketAccessPolicy}):
 * <pre>
 *   OPEN        -> IN_PROGRESS, CLOSED
 *   IN_PROGRESS -> IN_REVIEW, OPEN
 *   IN_REVIEW   -> CLOSED, IN_PROGRESS
 *   CLOSED      -> OPEN (reopen)
 * </pre>
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    IN_REVIEW,
    CLOSED;

    public Set<TicketStatus> allowedNext() {
        return switch (this) {
            case OPEN -> EnumSet.of(IN_PROGRESS, CLOSED);
            case IN_PROGRESS -> EnumSet.of(IN_REVIEW, OPEN);
            case IN_REVIEW -> EnumSet.of(CLOSED, IN_PROGRESS);
            case CLOSED -> EnumSet.of(OPEN);
        };
    }

    public boolean canMoveTo(TicketStatus target) {
        return allowedNext().contains(target);
    }
}
