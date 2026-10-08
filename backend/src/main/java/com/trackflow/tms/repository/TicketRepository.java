package com.trackflow.tms.repository;

import com.trackflow.tms.entity.Ticket;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    /** Search results with project, assignee and reporter fetched in the same query. */
    @Override
    @EntityGraph(attributePaths = {"project", "assignee", "reporter"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "project.owner", "assignee", "reporter"})
    @Query("select t from Ticket t where t.id = :id and t.deletedAt is null and t.project.deletedAt is null")
    Optional<Ticket> findActiveById(Long id);

    @EntityGraph(attributePaths = {"project", "project.owner", "assignee", "reporter"})
    @Query("""
            select t from Ticket t
            where t.project.projectKey = :projectKey and t.ticketNumber = :ticketNumber
              and t.deletedAt is null and t.project.deletedAt is null
            """)
    Optional<Ticket> findActiveByKey(String projectKey, int ticketNumber);
}
