package com.trackflow.tms.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicketStatusTest {

    @Test
    void workflowTableFromThePrd() {
        assertThat(TicketStatus.OPEN.allowedNext()).containsExactlyInAnyOrder(TicketStatus.IN_PROGRESS, TicketStatus.CLOSED);
        assertThat(TicketStatus.IN_PROGRESS.allowedNext()).containsExactlyInAnyOrder(TicketStatus.IN_REVIEW, TicketStatus.OPEN);
        assertThat(TicketStatus.IN_REVIEW.allowedNext()).containsExactlyInAnyOrder(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS);
        assertThat(TicketStatus.CLOSED.allowedNext()).containsExactly(TicketStatus.OPEN);
    }

    @Test
    void noStatusMovesToItself() {
        for (TicketStatus status : TicketStatus.values()) {
            assertThat(status.canMoveTo(status)).isFalse();
        }
    }
}
