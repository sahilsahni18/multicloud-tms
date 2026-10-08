package com.trackflow.tms.repository;

import com.trackflow.tms.entity.TicketHistory;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {

    @EntityGraph(attributePaths = "changedBy")
    @Query("select h from TicketHistory h where h.ticket.id = :ticketId order by h.changedAt, h.id")
    List<TicketHistory> findByTicketId(Long ticketId);
}
