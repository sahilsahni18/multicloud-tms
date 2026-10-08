-- =====================================================================
-- TrackFlow (Multi-Cloud Ticket Management System) - core schema
-- MySQL 8.0 / InnoDB / utf8mb4. All timestamps are stored in UTC.
--
-- Conventions
--   * Surrogate BIGINT keys; business keys get UNIQUE constraints.
--   * Audit columns on every business table: created_at, updated_at,
--     created_by, updated_by (user ids, no FK so audit survives deletes).
--   * `version` columns back JPA optimistic locking (@Version).
--   * Users, projects, tickets, comments and attachments are soft-deleted
--     (deleted_at) so history and FKs stay intact.
--   * Enumerations are VARCHAR + CHECK (maps to @Enumerated(STRING)).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Identity & access
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    id          SMALLINT     NOT NULL AUTO_INCREMENT,
    name        VARCHAR(32)  NOT NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name),
    CONSTRAINT ck_roles_name CHECK (name IN ('ADMIN', 'PROJECT_MANAGER', 'DEVELOPER', 'USER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at DATETIME(6)  NULL,
    deleted_at    DATETIME(6)  NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by    BIGINT       NULL,
    updated_by    BIGINT       NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    INDEX ix_users_enabled_deleted (enabled, deleted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id    BIGINT      NOT NULL,
    role_id    SMALLINT    NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BIGINT      NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id),
    INDEX ix_user_roles_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Refresh tokens are stored as SHA-256 hashes, never in plain text.
-- family_id groups a rotation chain so reuse of a revoked token can
-- revoke the whole family (token theft detection).
CREATE TABLE refresh_tokens (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    token_hash     CHAR(64)     NOT NULL,
    family_id      CHAR(36)     NOT NULL,
    expires_at     DATETIME(6)  NOT NULL,
    revoked_at     DATETIME(6)  NULL,
    replaced_by_id BIGINT       NULL,
    created_ip     VARCHAR(45)  NULL,
    user_agent     VARCHAR(255) NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    INDEX ix_refresh_tokens_user (user_id),
    INDEX ix_refresh_tokens_family (family_id),
    INDEX ix_refresh_tokens_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Projects
-- ---------------------------------------------------------------------
CREATE TABLE projects (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    project_key        VARCHAR(10)  NOT NULL,
    name               VARCHAR(120) NOT NULL,
    description        TEXT         NULL,
    owner_id           BIGINT       NOT NULL,
    -- Next number handed out for a ticket key (TMS-42). Incremented under a
    -- row lock when a ticket is created.
    next_ticket_number INT          NOT NULL DEFAULT 1,
    deleted_at         DATETIME(6)  NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by         BIGINT       NULL,
    updated_by         BIGINT       NULL,
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT uk_projects_key UNIQUE (project_key),
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT ck_projects_key CHECK (REGEXP_LIKE(project_key, '^[A-Z][A-Z0-9]{1,9}$', 'c')),
    CONSTRAINT ck_projects_next_number CHECK (next_ticket_number >= 1),
    INDEX ix_projects_owner (owner_id),
    INDEX ix_projects_deleted (deleted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_members (
    project_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BIGINT      NULL,
    CONSTRAINT pk_project_members PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX ix_project_members_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Tickets
-- ---------------------------------------------------------------------
CREATE TABLE tickets (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    project_id    BIGINT       NOT NULL,
    ticket_number INT          NOT NULL,
    title         VARCHAR(200) NOT NULL,
    description   TEXT         NULL,
    type          VARCHAR(16)  NOT NULL DEFAULT 'TASK',
    priority      VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    status        VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    reporter_id   BIGINT       NOT NULL,
    assignee_id   BIGINT       NULL,
    due_date      DATE         NULL,
    closed_at     DATETIME(6)  NULL,
    deleted_at    DATETIME(6)  NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by    BIGINT       NULL,
    updated_by    BIGINT       NULL,
    CONSTRAINT pk_tickets PRIMARY KEY (id),
    CONSTRAINT uk_tickets_project_number UNIQUE (project_id, ticket_number),
    CONSTRAINT fk_tickets_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_tickets_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_tickets_assignee FOREIGN KEY (assignee_id) REFERENCES users (id),
    CONSTRAINT ck_tickets_type CHECK (type IN ('BUG', 'TASK', 'STORY')),
    CONSTRAINT ck_tickets_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_tickets_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'IN_REVIEW', 'CLOSED')),
    CONSTRAINT ck_tickets_closed_at CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    -- Filter / dashboard access paths
    INDEX ix_tickets_project_status (project_id, status),
    INDEX ix_tickets_assignee_status (assignee_id, status),
    INDEX ix_tickets_reporter_status (reporter_id, status),
    INDEX ix_tickets_status_priority (status, priority),
    INDEX ix_tickets_priority (priority),
    INDEX ix_tickets_created_at (created_at),
    INDEX ix_tickets_closed_at (closed_at),
    INDEX ix_tickets_title (title)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE comments (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    ticket_id  BIGINT      NOT NULL,
    author_id  BIGINT      NOT NULL,
    body       TEXT        NOT NULL,
    edited_at  DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT      NULL,
    updated_by BIGINT      NULL,
    CONSTRAINT pk_comments PRIMARY KEY (id),
    CONSTRAINT fk_comments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users (id),
    INDEX ix_comments_ticket_created (ticket_id, created_at),
    INDEX ix_comments_author (author_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE attachments (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id        BIGINT       NOT NULL,
    uploaded_by      BIGINT       NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(127) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    storage_provider VARCHAR(16)  NOT NULL DEFAULT 'LOCAL',
    storage_key      VARCHAR(512) NOT NULL,
    checksum_sha256  CHAR(64)     NULL,
    deleted_at       DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by       BIGINT       NULL,
    updated_by       BIGINT       NULL,
    CONSTRAINT pk_attachments PRIMARY KEY (id),
    CONSTRAINT uk_attachments_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_attachments_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_attachments_uploader FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT ck_attachments_size CHECK (size_bytes BETWEEN 0 AND 10485760),
    CONSTRAINT ck_attachments_provider CHECK (storage_provider IN ('LOCAL', 'S3', 'AZURE_BLOB')),
    INDEX ix_attachments_ticket (ticket_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Audit trail
-- ---------------------------------------------------------------------
-- Field-level changes on a ticket (drives the ticket timeline).
CREATE TABLE ticket_history (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    ticket_id   BIGINT        NOT NULL,
    changed_by  BIGINT        NULL,
    change_type VARCHAR(24)   NOT NULL,
    field_name  VARCHAR(64)   NULL,
    old_value   VARCHAR(1000) NULL,
    new_value   VARCHAR(1000) NULL,
    changed_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ticket_history PRIMARY KEY (id),
    CONSTRAINT fk_ticket_history_ticket FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_history_user FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_ticket_history_type CHECK (change_type IN ('CREATED', 'ASSIGNED', 'UPDATED', 'STATUS_CHANGED')),
    INDEX ix_ticket_history_ticket_time (ticket_id, changed_at),
    INDEX ix_ticket_history_user (changed_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- System-wide activity feed (tickets, projects, users, deployments).
CREATE TABLE activity_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT       NULL,
    action      VARCHAR(48)  NOT NULL,
    entity_type VARCHAR(32)  NOT NULL,
    entity_id   BIGINT       NOT NULL,
    project_id  BIGINT       NULL,
    summary     VARCHAR(500) NOT NULL,
    metadata    JSON         NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_activity_logs PRIMARY KEY (id),
    CONSTRAINT fk_activity_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_activity_logs_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE SET NULL,
    INDEX ix_activity_logs_entity (entity_type, entity_id),
    INDEX ix_activity_logs_project_time (project_id, created_at),
    INDEX ix_activity_logs_actor_time (actor_id, created_at),
    INDEX ix_activity_logs_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- One-click deployment portal
-- ---------------------------------------------------------------------
-- Status is validated in the application (the step list may grow), the
-- request parameters are validated here.
CREATE TABLE deployments (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    cloud_provider   VARCHAR(8)    NOT NULL,
    region           VARCHAR(32)   NOT NULL,
    cluster_count    TINYINT       NOT NULL,
    environment      VARCHAR(8)    NOT NULL,
    action           VARCHAR(8)    NOT NULL DEFAULT 'APPLY',
    status           VARCHAR(24)   NOT NULL DEFAULT 'QUEUED',
    runner           VARCHAR(16)   NOT NULL,
    external_run_id  VARCHAR(64)   NULL,
    external_run_url VARCHAR(512)  NULL,
    error_message    VARCHAR(2000) NULL,
    requested_by     BIGINT        NOT NULL,
    started_at       DATETIME(6)   NULL,
    finished_at      DATETIME(6)   NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by       BIGINT        NULL,
    updated_by       BIGINT        NULL,
    CONSTRAINT pk_deployments PRIMARY KEY (id),
    CONSTRAINT fk_deployments_requested_by FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT ck_deployments_cloud CHECK (cloud_provider IN ('AWS', 'AZURE')),
    CONSTRAINT ck_deployments_clusters CHECK (cluster_count BETWEEN 1 AND 5),
    CONSTRAINT ck_deployments_env CHECK (environment IN ('DEV', 'QA', 'PROD')),
    CONSTRAINT ck_deployments_action CHECK (action IN ('PLAN', 'APPLY', 'DESTROY')),
    CONSTRAINT ck_deployments_runner CHECK (runner IN ('GITHUB_ACTIONS', 'LOCAL', 'SIMULATED')),
    INDEX ix_deployments_status (status),
    INDEX ix_deployments_target (cloud_provider, region, environment),
    INDEX ix_deployments_requested_by (requested_by),
    INDEX ix_deployments_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Every step reported by the provisioning pipeline (drives the live stepper).
CREATE TABLE deployment_events (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    deployment_id BIGINT        NOT NULL,
    status        VARCHAR(24)   NOT NULL,
    message       VARCHAR(2000) NULL,
    occurred_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_deployment_events PRIMARY KEY (id),
    CONSTRAINT fk_deployment_events_deployment FOREIGN KEY (deployment_id) REFERENCES deployments (id) ON DELETE CASCADE,
    INDEX ix_deployment_events_deployment_time (deployment_id, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Reference data (needed in every environment)
-- ---------------------------------------------------------------------
INSERT INTO roles (name, description) VALUES
    ('ADMIN',           'Manage users, projects, tickets and deployments'),
    ('PROJECT_MANAGER', 'Create projects, assign tickets, view project metrics'),
    ('DEVELOPER',       'Work assigned tickets, update status, comment'),
    ('USER',            'Create and follow own tickets, comment');
