package com.trackflow.tms.repository;

import com.trackflow.tms.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long>, JpaSpecificationExecutor<ActivityLog> {

    @Override
    @EntityGraph(attributePaths = "actor")
    Page<ActivityLog> findAll(Specification<ActivityLog> spec, Pageable pageable);
}
