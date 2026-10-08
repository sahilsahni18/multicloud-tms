package com.trackflow.tms.service;

import com.trackflow.tms.dto.deployment.DeploymentDetailResponse;
import com.trackflow.tms.service.provisioning.DeploymentEvents;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events for the portal's live stepper. Each update carries the
 * full deployment detail, so a client that misses an event is still correct.
 * Subscribers are per pod; the UI also polls as a fallback when several
 * backend replicas run behind a load balancer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentStreamService {

    private static final Duration TIMEOUT = Duration.ofMinutes(30);
    private static final String EVENT_NAME = "deployment";

    private final DeploymentService deployments;
    private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long deploymentId) {
        DeploymentDetailResponse snapshot = deployments.detail(deploymentId);
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        if (snapshot.deployment().status().isTerminal()) {
            send(deploymentId, emitter, snapshot);
            emitter.complete();
            return emitter;
        }
        subscribers.computeIfAbsent(deploymentId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> unsubscribe(deploymentId, emitter));
        emitter.onTimeout(() -> unsubscribe(deploymentId, emitter));
        emitter.onError(error -> unsubscribe(deploymentId, emitter));
        send(deploymentId, emitter, snapshot);
        return emitter;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onChanged(DeploymentEvents.Changed event) {
        Set<SseEmitter> emitters = subscribers.get(event.deploymentId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        DeploymentDetailResponse detail = deployments.detail(event.deploymentId());
        for (SseEmitter emitter : emitters) {
            send(event.deploymentId(), emitter, detail);
            if (detail.deployment().status().isTerminal()) {
                emitter.complete();
            }
        }
    }

    private void send(Long deploymentId, SseEmitter emitter, DeploymentDetailResponse detail) {
        try {
            emitter.send(SseEmitter.event().name(EVENT_NAME).id(String.valueOf(detail.events().size()))
                    .data(detail));
        } catch (IOException | IllegalStateException e) {
            log.debug("Dropping SSE subscriber for deployment {}: {}", deploymentId, e.getMessage());
            unsubscribe(deploymentId, emitter);
        }
    }

    private void unsubscribe(Long deploymentId, SseEmitter emitter) {
        subscribers.computeIfPresent(deploymentId, (id, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }
}
