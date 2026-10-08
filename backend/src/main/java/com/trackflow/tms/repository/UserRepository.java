package com.trackflow.tms.repository;

import com.trackflow.tms.entity.RoleName;
import com.trackflow.tms.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    /** The email column uses a case-insensitive collation, so callers only need to trim. */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByEmail(String email);

    /** Admin user list. Roles load in batches (see User#roles). */
    @Query("""
            select u from User u
            where u.deletedAt is null
              and (:q is null or u.email like concat('%', :q, '%') or u.fullName like concat('%', :q, '%'))
              and (:enabled is null or u.enabled = :enabled)
              and (:role is null or exists (select 1 from User u2 join u2.roles r where u2 = u and r.name = :role))
            """)
    Page<User> search(String q, RoleName role, Boolean enabled, Pageable pageable);

    /** [roleName, activeUserCount] */
    @Query("select r.name, count(u) from User u join u.roles r where u.deletedAt is null group by r.name")
    List<Object[]> countByRole();

    /** Updates the login timestamp without bumping the optimistic-lock version. */
    @Modifying
    @Query("update User u set u.lastLoginAt = :at where u.id = :id")
    void updateLastLoginAt(Long id, Instant at);
}
