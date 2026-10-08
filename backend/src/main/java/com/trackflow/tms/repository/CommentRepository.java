package com.trackflow.tms.repository;

import com.trackflow.tms.entity.Comment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = "author")
    @Query("select c from Comment c where c.ticket.id = :ticketId and c.deletedAt is null order by c.createdAt, c.id")
    List<Comment> findLiveByTicketId(Long ticketId);

    @EntityGraph(attributePaths = {"author", "ticket", "ticket.project", "ticket.assignee", "ticket.reporter"})
    @Query("select c from Comment c where c.id = :id and c.deletedAt is null")
    Optional<Comment> findLiveById(Long id);
}
