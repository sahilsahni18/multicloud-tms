# Database design

MySQL 8.0, InnoDB, utf8mb4, all timestamps UTC. Schema is owned by Flyway:

| Location | Contents | Loaded in |
|---|---|---|
| `backend/src/main/resources/db/migration` | `V1__init_schema.sql` — tables, constraints, indexes, the 4 roles | every environment |
| `backend/src/main/resources/db/demo` | `V1_1__demo_seed.sql` — 5 users, 2 projects, 11 tickets, history, comments | local / dev / QA only |

Demo logins (password `Password@123`): `admin@`, `pm@`, `dev@`, `dev2@`, `user@trackflow.dev`.

## ER diagram

Audit columns (`created_at`, `updated_at`, `created_by`, `updated_by`) and `version` are omitted below for readability; every business table has them.

```mermaid
erDiagram
    users ||--o{ user_roles : has
    roles ||--o{ user_roles : grants
    users ||--o{ refresh_tokens : owns
    users ||--o{ projects : owns
    projects ||--o{ project_members : has
    users ||--o{ project_members : "member of"
    projects ||--o{ tickets : contains
    users ||--o{ tickets : reports
    users |o--o{ tickets : "assigned to"
    tickets ||--o{ comments : has
    users ||--o{ comments : writes
    tickets ||--o{ attachments : has
    tickets ||--o{ ticket_history : "changes"
    users |o--o{ ticket_history : "changed by"
    users |o--o{ activity_logs : performs
    projects |o--o{ activity_logs : scopes
    users ||--o{ deployments : requests
    deployments ||--o{ deployment_events : reports

    users {
        bigint id PK
        varchar email UK
        varchar password_hash "BCrypt"
        varchar full_name
        boolean enabled
        datetime last_login_at
        datetime deleted_at "soft delete"
    }
    roles {
        smallint id PK
        varchar name UK "ADMIN | PROJECT_MANAGER | DEVELOPER | USER"
    }
    user_roles {
        bigint user_id PK,FK
        smallint role_id PK,FK
    }
    refresh_tokens {
        bigint id PK
        bigint user_id FK
        char token_hash UK "SHA-256"
        char family_id "rotation chain"
        datetime expires_at
        datetime revoked_at
        bigint replaced_by_id FK
    }
    projects {
        bigint id PK
        varchar project_key UK "e.g. TMS"
        varchar name
        bigint owner_id FK
        int next_ticket_number
        datetime deleted_at
    }
    project_members {
        bigint project_id PK,FK
        bigint user_id PK,FK
    }
    tickets {
        bigint id PK
        bigint project_id FK
        int ticket_number "UK with project_id"
        varchar title
        varchar type "BUG | TASK | STORY"
        varchar priority "LOW | MEDIUM | HIGH | CRITICAL"
        varchar status "OPEN | IN_PROGRESS | IN_REVIEW | CLOSED"
        bigint reporter_id FK
        bigint assignee_id FK
        date due_date
        datetime closed_at
        datetime deleted_at
    }
    comments {
        bigint id PK
        bigint ticket_id FK
        bigint author_id FK
        text body
        datetime edited_at
        datetime deleted_at
    }
    attachments {
        bigint id PK
        bigint ticket_id FK
        bigint uploaded_by FK
        varchar file_name
        bigint size_bytes "max 10 MB"
        varchar storage_provider "LOCAL | S3 | AZURE_BLOB"
        varchar storage_key UK
    }
    ticket_history {
        bigint id PK
        bigint ticket_id FK
        bigint changed_by FK
        varchar change_type "CREATED | ASSIGNED | UPDATED | STATUS_CHANGED"
        varchar field_name
        varchar old_value
        varchar new_value
        datetime changed_at
    }
    activity_logs {
        bigint id PK
        bigint actor_id FK
        varchar action
        varchar entity_type
        bigint entity_id
        bigint project_id FK
        varchar summary
        json metadata
    }
    deployments {
        bigint id PK
        varchar cloud_provider "AWS | AZURE"
        varchar region
        tinyint cluster_count "1-5"
        varchar environment "DEV | QA | PROD"
        varchar action "PLAN | APPLY | DESTROY"
        varchar status
        varchar runner "GITHUB_ACTIONS | LOCAL | SIMULATED"
        varchar external_run_url
        bigint requested_by FK
    }
    deployment_events {
        bigint id PK
        bigint deployment_id FK
        varchar status
        varchar message
        datetime occurred_at
    }
```

## Design decisions

| Decision | Why |
|---|---|
| Ticket key = `project_key` + `ticket_number`, number allocated from `projects.next_ticket_number` under a row lock | Human-readable keys (`TMS-42`) without gaps from rolled-back inserts racing each other |
| `CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))` | Time-to-close metrics can trust `closed_at`; reopen must clear it |
| Enums as `VARCHAR` + `CHECK` instead of MySQL `ENUM` | Maps cleanly to JPA `@Enumerated(STRING)`; adding a value is a one-line migration |
| Soft delete on users / projects / tickets / comments / attachments | Keeps history, FKs and dashboards consistent; admin "delete user" deactivates |
| Audit `created_by` / `updated_by` without FKs | Audit data must survive user removal; filled by Spring Data auditing |
| Refresh tokens hashed, grouped by `family_id` | A stolen refresh token that is replayed after rotation revokes the whole family |
| Deployment `status` validated in code, request fields by `CHECK` | The step list may grow; the request parameters are fixed by the PRD |

## Indexes and the queries they serve

| Index | Query |
|---|---|
| `tickets (project_id, status)` | Project board / list filtered by status, dashboard per project |
| `tickets (assignee_id, status)` | "My tickets" for developers, productivity per assignee |
| `tickets (reporter_id, status)` | "My tickets" for USER role |
| `tickets (status, priority)`, `tickets (priority)` | Dashboard by status / priority, priority filter |
| `tickets (created_at)`, `tickets (closed_at)` | Sorting, closed-per-week and time-to-close metrics |
| `tickets (title)` | Prefix search on title |
| `ticket_history (ticket_id, changed_at)`, `comments (ticket_id, created_at)` | Ticket timeline |
| `activity_logs (project_id, created_at)`, `(actor_id, created_at)` | Activity feeds |
| `deployments (cloud_provider, region, environment)` | Deployment history filters |
