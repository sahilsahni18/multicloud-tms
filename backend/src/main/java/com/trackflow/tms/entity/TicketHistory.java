package com.trackflow.tms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One field-level change on a ticket. Append-only. */
@Entity
@Table(name = "ticket_history")
@Getter
@Setter
@NoArgsConstructor
public class TicketHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", updatable = false)
    private User changedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 24, updatable = false)
    private HistoryChangeType changeType;

    @Column(name = "field_name", length = 64, updatable = false)
    private String fieldName;

    @Column(name = "old_value", length = 1000, updatable = false)
    private String oldValue;

    @Column(name = "new_value", length = 1000, updatable = false)
    private String newValue;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;
}
