package com.trackflow.tms.service;

import com.trackflow.tms.config.DeploymentProperties;
import com.trackflow.tms.dto.common.PageResponse;
import com.trackflow.tms.dto.deployment.CreateDeploymentRequest;
import com.trackflow.tms.dto.deployment.DeploymentDetailResponse;
import com.trackflow.tms.dto.deployment.DeploymentOptionsResponse;
import com.trackflow.tms.dto.deployment.DeploymentResponse;
import com.trackflow.tms.entity.Deployment;
import com.trackflow.tms.entity.DeploymentAction;
import com.trackflow.tms.entity.DeploymentEnvironment;
import com.trackflow.tms.entity.DeploymentEvent;
import com.trackflow.tms.entity.DeploymentStatus;
import com.trackflow.tms.exception.BadRequestException;
import com.trackflow.tms.exception.ConflictException;
import com.trackflow.tms.exception.NotFoundException;
import com.trackflow.tms.mapper.DeploymentMapper;
import com.trackflow.tms.repository.DeploymentEventRepository;
import com.trackflow.tms.repository.DeploymentRepository;
import com.trackflow.tms.repository.UserRepository;
import com.trackflow.tms.security.AuthUser;
import com.trackflow.tms.service.provisioning.DeploymentEvents;
import com.trackflow.tms.service.provisioning.DeploymentRun;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-click Deployment Portal. A request is validated and stored as QUEUED;
 * after commit it is handed to the configured runner, which reports each step
 * back through {@link #applyEvent} (in-process for the simulator, via the
 * signed callback endpoint for GitHub Actions).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentService {

    public static final int MIN_CLUSTERS = 1;
    public static final int MAX_CLUSTERS = 5;
    private static final List<DeploymentStatus> TERMINAL = Arrays.stream(DeploymentStatus.values())
            .filter(DeploymentStatus::isTerminal).toList();

    private final DeploymentRepository deploymentRepository;
    private final DeploymentMapper deploymentMapper;
    private final DeploymentEventRepository eventRepository;
    private final UserRepository userRepository;
    private final DeploymentProperties properties;
    private final ActivityService activity;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public DeploymentOptionsResponse options() {
        return new DeploymentOptionsResponse(properties.regions(), List.of(DeploymentEnvironment.values()),
                properties.applyEnvironments(), MIN_CLUSTERS, MAX_CLUSTERS, properties.runner());
    }

    @Transactional
    public DeploymentDetailResponse request(AuthUser user, CreateDeploymentRequest request) {
        DeploymentAction action = request.action() == null ? DeploymentAction.APPLY : request.action();
        if (action == DeploymentAction.DESTROY) {
            throw new BadRequestException("To destroy, use POST /api/v1/deployments/{id}/destroy");
        }
        List<String> regions = properties.regions().getOrDefault(request.cloudProvider(), List.of());
        if (!regions.contains(request.region())) {
            throw new BadRequestException("Region " + request.region() + " is not available for "
                    + request.cloudProvider() + ". Choose one of " + regions);
        }
        if (request.environment() == DeploymentEnvironment.PROD
                && !"PROD".equals(request.confirmEnvironment() == null ? null : request.confirmEnvironment().trim())) {
            throw new BadRequestException("Type PROD in confirmEnvironment to deploy to production");
        }
        if (action == DeploymentAction.APPLY && !properties.applyEnvironments().contains(request.environment())) {
            throw new BadRequestException(request.environment() + " can only be planned in this installation "
                    + "(apply is enabled for " + properties.applyEnvironments() + "). Use action PLAN.");
        }
        if (deploymentRepository.existsByCloudProviderAndRegionAndEnvironmentAndStatusNotIn(
                request.cloudProvider(), request.region(), request.environment(), TERMINAL)) {
            throw new ConflictException("A deployment to " + request.cloudProvider() + " " + request.region()
                    + " " + request.environment() + " is already running");
        }

        Deployment deployment = new Deployment();
        deployment.setCloudProvider(request.cloudProvider());
        deployment.setRegion(request.region());
        deployment.setClusterCount(request.clusterCount());
        deployment.setEnvironment(request.environment());
        deployment.setAction(action);
        deployment.setStatus(DeploymentStatus.QUEUED);
        deployment.setRunner(properties.runner());
        deployment.setRequestedBy(userRepository.getReferenceById(user.getId()));
        deploymentRepository.save(deployment);

        addEvent(deployment, DeploymentStatus.QUEUED, action + " requested by " + user.getEmail()
                + " (" + request.clusterCount() + " cluster(s), runner " + properties.runner() + ")");
        activity.log(user, ActivityActions.DEPLOYMENT_REQUESTED, ActivityActions.ENTITY_DEPLOYMENT,
                deployment.getId(), null, action + " " + request.cloudProvider() + " " + request.region() + " "
                        + request.environment() + " x" + request.clusterCount(),
                Map.of("cloud", request.cloudProvider().name(), "region", request.region(),
                        "environment", request.environment().name(), "clusters", request.clusterCount()));
        publisher.publishEvent(new DeploymentEvents.StartRequested(DeploymentRun.of(deployment)));
        return toDetail(deployment);
    }

    /** Tears down what an APPLY created. Allowed once the apply has finished (completed or failed). */
    @Transactional
    public DeploymentDetailResponse destroy(AuthUser user, Long id) {
        Deployment deployment = deploymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.of("Deployment", id));
        if (deployment.getAction() != DeploymentAction.APPLY
                || (deployment.getStatus() != DeploymentStatus.COMPLETED
                    && deployment.getStatus() != DeploymentStatus.FAILED)) {
            throw new ConflictException("Only a finished APPLY deployment can be destroyed (this one is "
                    + deployment.getAction() + " / " + deployment.getStatus() + ")");
        }
        deployment.setAction(DeploymentAction.DESTROY);
        deployment.setStatus(DeploymentStatus.DESTROYING);
        deployment.setFinishedAt(null);
        deployment.setErrorMessage(null);
        addEvent(deployment, DeploymentStatus.DESTROYING, "Destroy requested by " + user.getEmail());
        activity.log(user, ActivityActions.DEPLOYMENT_DESTROY_REQUESTED, ActivityActions.ENTITY_DEPLOYMENT,
                deployment.getId(), null, "DESTROY " + deployment.getCloudProvider() + " " + deployment.getRegion()
                        + " " + deployment.getEnvironment(), null);
        publisher.publishEvent(new DeploymentEvents.StartRequested(DeploymentRun.of(deployment)));
        publisher.publishEvent(new DeploymentEvents.Changed(deployment.getId()));
        return toDetail(deployment);
    }

    /**
     * Records a step reported by a runner. Runs in its own transaction: it is
     * called from runner threads, the callback endpoint and after-commit listeners.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyEvent(Long id, DeploymentStatus status, String message, String runId, String runUrl) {
        Deployment deployment = deploymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.of("Deployment", id));
        if (deployment.getStatus().isTerminal()) {
            throw new ConflictException("Deployment " + id + " already finished as " + deployment.getStatus());
        }
        if (status != DeploymentStatus.FAILED && !expectedSteps(deployment.getAction()).contains(status)) {
            throw new BadRequestException(status + " is not a step of a " + deployment.getAction() + " deployment");
        }

        Instant now = clock.instant();
        deployment.setStatus(status);
        if (deployment.getStartedAt() == null) {
            deployment.setStartedAt(now);
        }
        if (status.isTerminal()) {
            deployment.setFinishedAt(now);
        }
        if (status == DeploymentStatus.FAILED) {
            deployment.setErrorMessage(message);
        }
        if (runId != null) {
            deployment.setExternalRunId(runId);
        }
        if (runUrl != null) {
            deployment.setExternalRunUrl(runUrl);
        }
        addEvent(deployment, status, message);
        log.info("Deployment {} -> {}", id, status);
        publisher.publishEvent(new DeploymentEvents.Changed(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<DeploymentResponse> history(Pageable pageable) {
        return PageResponse.of(deploymentRepository.findPage(pageable), deploymentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DeploymentDetailResponse detail(Long id) {
        return toDetail(deploymentRepository.findWithRequester(id)
                .orElseThrow(() -> NotFoundException.of("Deployment", id)));
    }

    public static List<DeploymentStatus> expectedSteps(DeploymentAction action) {
        return switch (action) {
            case APPLY -> DeploymentStatus.APPLY_STEPS;
            case PLAN -> DeploymentStatus.PLAN_STEPS;
            case DESTROY -> DeploymentStatus.DESTROY_STEPS;
        };
    }

    private void addEvent(Deployment deployment, DeploymentStatus status, String message) {
        DeploymentEvent event = new DeploymentEvent();
        event.setDeploymentId(deployment.getId());
        event.setStatus(status);
        event.setMessage(message == null ? null : message.substring(0, Math.min(message.length(), 2000)));
        event.setOccurredAt(clock.instant());
        eventRepository.save(event);
    }

    private DeploymentDetailResponse toDetail(Deployment deployment) {
        deploymentRepository.flush();
        return new DeploymentDetailResponse(
                deploymentMapper.toResponse(deployment),
                expectedSteps(deployment.getAction()),
                eventRepository.findByDeploymentIdOrderByOccurredAtAscIdAsc(deployment.getId()).stream()
                        .map(deploymentMapper::toEvent)
                        .toList());
    }
}
