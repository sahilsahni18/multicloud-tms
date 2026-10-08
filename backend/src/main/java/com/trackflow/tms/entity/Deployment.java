package com.trackflow.tms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One request from the Deployment Portal and its current state. */
@Entity
@Table(name = "deployments")
@Getter
@Setter
@NoArgsConstructor
public class Deployment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "cloud_provider", nullable = false, length = 8, updatable = false)
    private CloudProvider cloudProvider;

    @Column(nullable = false, length = 32, updatable = false)
    private String region;

    @Column(name = "cluster_count", nullable = false, columnDefinition = "tinyint", updatable = false)
    private Integer clusterCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, updatable = false)
    private DeploymentEnvironment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private DeploymentAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DeploymentStatus status = DeploymentStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RunnerType runner;

    @Column(name = "external_run_id", length = 64)
    private String externalRunId;

    @Column(name = "external_run_url", length = 512)
    private String externalRunUrl;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false, updatable = false)
    private User requestedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    private Long version;
}
