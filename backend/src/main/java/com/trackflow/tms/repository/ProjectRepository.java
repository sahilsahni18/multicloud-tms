package com.trackflow.tms.repository;

import com.trackflow.tms.entity.Project;
import com.trackflow.tms.entity.TicketStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @EntityGraph(attributePaths = "owner")
    Optional<Project> findByIdAndDeletedAtIsNull(Long id);

    /** Row lock used when allocating the next ticket number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id and p.deletedAt is null")
    Optional<Project> findActiveByIdForUpdate(Long id);

    boolean existsByProjectKey(String projectKey);

    @Query("""
            select case when count(m) > 0 then true else false end
            from Project p join p.members m where p.id = :projectId and m.id = :userId
            """)
    boolean isMember(Long projectId, Long userId);

    /** All projects (admin) or those the user owns or belongs to. */
    @EntityGraph(attributePaths = "owner")
    @Query(value = """
            select p from Project p
            where p.deletedAt is null
              and (:all = true
                   or p.owner.id = :userId
                   or exists (select 1 from Project p2 join p2.members m where p2 = p and m.id = :userId))
              and (:q is null or p.name like concat('%', :q, '%') or p.projectKey like concat('%', :q, '%'))
            """)
    Page<Project> findVisible(boolean all, Long userId, String q, Pageable pageable);

    /** Ids of every live project the user owns or belongs to. */
    @Query("""
            select p.id from Project p
            where p.deletedAt is null
              and (p.owner.id = :userId
                   or exists (select 1 from Project p2 join p2.members m where p2 = p and m.id = :userId))
            """)
    List<Long> findAccessibleProjectIds(Long userId);

    /** [projectId, memberCount] */
    @Query("select p.id, count(m) from Project p join p.members m where p.id in :ids group by p.id")
    List<Object[]> countMembers(Collection<Long> ids);

    /** [projectId, totalTickets, openTickets] */
    @Query("""
            select t.project.id, count(t), sum(case when t.status <> :closed then 1 else 0 end)
            from Ticket t
            where t.project.id in :ids and t.deletedAt is null
            group by t.project.id
            """)
    List<Object[]> countTickets(Collection<Long> ids, TicketStatus closed);
}
