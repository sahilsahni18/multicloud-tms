package com.trackflow.tms.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.tms.dto.common.PageResponse;
import com.trackflow.tms.dto.deployment.CreateDeploymentRequest;
import com.trackflow.tms.dto.deployment.DeploymentCallbackRequest;
import com.trackflow.tms.dto.deployment.DeploymentDetailResponse;
import com.trackflow.tms.dto.deployment.DeploymentOptionsResponse;
import com.trackflow.tms.dto.deployment.DeploymentResponse;
import com.trackflow.tms.exception.BadRequestException;
import com.trackflow.tms.security.AuthUser;
import com.trackflow.tms.security.DeploymentCallbackVerifier;
import com.trackflow.tms.service.DeploymentService;
import com.trackflow.tms.service.DeploymentStreamService;
import com.trackflow.tms.util.SortGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/deployments")
@RequiredArgsConstructor
@Tag(name = "Deployments", description = "One-click Deployment Portal (ADMIN)")
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final DeploymentStreamService streamService;
    private final DeploymentCallbackVerifier callbackVerifier;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @GetMapping("/options")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Clouds, regions, environments and limits for the deploy form")
    public DeploymentOptionsResponse options() {
        return deploymentService.options();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Start a deployment; returns 202 and the QUEUED deployment",
            description = "PROD requires confirmEnvironment=PROD. Environments not enabled for APPLY accept PLAN only.")
    public ResponseEntity<DeploymentDetailResponse> create(@AuthenticationPrincipal AuthUser user,
                                                           @Valid @RequestBody CreateDeploymentRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(deploymentService.request(user, request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deployment history, newest first")
    public PageResponse<DeploymentResponse> history(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return deploymentService.history(SortGuard.require(pageable,
                Set.of("createdAt", "startedAt", "finishedAt", "status", "cloudProvider", "environment", "region")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "A deployment with its step events")
    public DeploymentDetailResponse get(@PathVariable Long id) {
        return deploymentService.detail(id);
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Live status as Server-Sent Events (event name: deployment)")
    public SseEmitter stream(@PathVariable Long id) {
        return streamService.subscribe(id);
    }

    @PostMapping("/{id}/destroy")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tear down a finished APPLY deployment (tofu destroy)")
    public ResponseEntity<DeploymentDetailResponse> destroy(@AuthenticationPrincipal AuthUser user,
                                                            @PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(deploymentService.destroy(user, id));
    }

    /**
     * Step report from the provisioning pipeline. Not a user endpoint: it is
     * authenticated by an HMAC signature instead of a JWT.
     */
    @PostMapping(value = "/{id}/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("permitAll()")
    @SecurityRequirements
    @Operation(summary = "Pipeline callback (HMAC-signed, no JWT)",
            description = "Headers: X-TrackFlow-Timestamp (unix seconds) and "
                    + "X-TrackFlow-Signature: sha256=hex(HMAC_SHA256(secret, timestamp + '.' + body))")
    public ResponseEntity<Void> callback(
            @PathVariable Long id,
            @RequestHeader(DeploymentCallbackVerifier.TIMESTAMP_HEADER) String timestamp,
            @RequestHeader(DeploymentCallbackVerifier.SIGNATURE_HEADER) String signature,
            @RequestBody String body) {
        callbackVerifier.verify(timestamp, signature, body);
        DeploymentCallbackRequest event;
        try {
            event = objectMapper.readValue(body, DeploymentCallbackRequest.class);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Callback body is not valid JSON for a deployment event");
        }
        Set<ConstraintViolation<DeploymentCallbackRequest>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new BadRequestException("Invalid callback: " + violations.iterator().next().getPropertyPath()
                    + " " + violations.iterator().next().getMessage());
        }
        deploymentService.applyEvent(id, event.status(), event.message(), event.runId(), event.runUrl());
        return ResponseEntity.noContent().build();
    }
}
