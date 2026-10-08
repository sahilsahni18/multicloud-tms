package com.trackflow.tms.repository;

import com.trackflow.tms.entity.DeploymentEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentEventRepository extends JpaRepository<DeploymentEvent, Long> {

    List<DeploymentEvent> findByDeploymentIdOrderByOccurredAtAscIdAsc(Long deploymentId);
}
