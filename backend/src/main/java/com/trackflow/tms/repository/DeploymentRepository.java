package com.trackflow.tms.repository;

import com.trackflow.tms.entity.CloudProvider;
import com.trackflow.tms.entity.Deployment;
import com.trackflow.tms.entity.DeploymentEnvironment;
import com.trackflow.tms.entity.DeploymentStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    @EntityGraph(attributePaths = "requestedBy")
    @Query("select d from Deployment d")
    Page<Deployment> findPage(Pageable pageable);

    @EntityGraph(attributePaths = "requestedBy")
    @Query("select d from Deployment d where d.id = :id")
    Optional<Deployment> findWithRequester(Long id);

    /** Serialises concurrent status updates for one deployment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Deployment d where d.id = :id")
    Optional<Deployment> findByIdForUpdate(Long id);

    boolean existsByCloudProviderAndRegionAndEnvironmentAndStatusNotIn(
            CloudProvider cloudProvider, String region, DeploymentEnvironment environment,
            Collection<DeploymentStatus> statuses);
}
