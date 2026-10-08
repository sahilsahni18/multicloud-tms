# API reference

Base path `/api/v1`. All endpoints except the public ones need `Authorization: Bearer <accessToken>`. The interactive version with request/response schemas is Swagger UI at `/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`).

Conventions:
- **Errors** are RFC 7807 `application/problem+json` (see [security-flow.md](../architecture/security-flow.md#error-format)).
- **401** missing/invalid token · **403** your role or relationship does not allow it · **404** not found *or not visible to you* (hidden tickets and projects are never confirmed to exist) · **409** workflow or concurrency conflict.
- **Paging**: `?page=0&size=20&sort=field,desc`; max `size` is 100. Paged responses are `{content, page, size, totalElements, totalPages}`.
- **Nulls** are omitted from responses.

## Auth (public)

| Method | Path | Who | Notes |
|---|---|---|---|
| POST | `/auth/register` | anyone | New account gets USER; signs in |
| POST | `/auth/login` | anyone | Returns access token; sets `tf_refresh` cookie |
| POST | `/auth/refresh` | cookie | Rotates the refresh cookie |
| POST | `/auth/logout` | cookie | Revokes the refresh token |
| GET | `/auth/me` | any role | Current user and roles |

## Users and roles

| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/users?q=&role=&enabled=` | ADMIN | Sort: createdAt, email, fullName, lastLoginAt |
| GET | `/users/lookup?q=` | ADMIN, PM | 20 active users, for pickers |
| GET | `/users/{id}` | ADMIN | |
| POST | `/users` | ADMIN | `{email, fullName, password, roles[], enabled?}` |
| PUT | `/users/{id}` | ADMIN | `{email, fullName, enabled}`; disabling signs the user out |
| PUT | `/users/{id}/roles` | ADMIN | `{roles[]}`; cannot remove your own ADMIN |
| PUT | `/users/{id}/password` | ADMIN | `{newPassword}`: reset a forgotten password; signs the user out everywhere. Not for your own account (use `/users/me/password`) |
| DELETE | `/users/{id}` | ADMIN | Soft delete; cannot delete yourself |
| PUT | `/users/me` | any role | `{fullName}` |
| PUT | `/users/me/password` | any role | `{currentPassword, newPassword}`; signs out everywhere |
| GET | `/roles` | ADMIN | Roles with active-user counts |

## Projects

| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/projects?q=` | any role | Admin: all; others: projects they own or belong to |
| GET | `/projects/{id}` | visible | Includes member and ticket counts, `canManage` |
| POST | `/projects` | ADMIN, PM | `{key, name, description?, ownerId? (admin only)}`; key is 2-10 chars, immutable |
| PUT | `/projects/{id}` | ADMIN, owning PM | `{name, description}` |
| DELETE | `/projects/{id}` | ADMIN | Soft delete; hides its tickets |
| GET | `/projects/{id}/members` | visible | Owner first |
| POST | `/projects/{id}/members` | ADMIN, owning PM | `{userId}` |
| DELETE | `/projects/{id}/members/{userId}` | ADMIN, owning PM | Owner cannot be removed |

## Tickets

| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/tickets` | any role | Filters below; only tickets you may see |
| GET | `/tickets/export` | ADMIN, PM | Same filters, `text/csv`, max 5000 rows |
| GET | `/tickets/{id}` | visible | Includes `allowedTransitions` and `permissions` for you |
| GET | `/tickets/key/{key}` | visible | e.g. `/tickets/key/TMS-4` |
| POST | `/tickets` | project member | `{projectId, title, description?, type?, priority?, assigneeId? (ADMIN/PM), dueDate?}` |
| PUT | `/tickets/{id}` | manager, assignee, reporter while OPEN | `{title, description, type, priority, dueDate, version}`; stale `version` -> 409 |
| PUT | `/tickets/{id}/assignee` | ADMIN, PM of the project | `{assigneeId}` or `{}` to unassign; must be a project member |
| POST | `/tickets/{id}/transitions` | see workflow | `{status}`; illegal move -> 409, not yours -> 403 |
| DELETE | `/tickets/{id}` | ADMIN, PM of the project | Soft delete |
| GET | `/tickets/{id}/history` | visible | Field-level changes |
| GET | `/tickets/{id}/timeline` | visible | Comments + changes, oldest first |

**Search filters** (combine with AND; repeat a parameter for OR): `projectId`, `status`, `priority`, `type`, `assigneeId`, `unassigned=true`, `reporterId`, `q` (title text or exact key like `TMS-4`).
**Sort**: `createdAt` (default, desc), `updatedAt`, `dueDate`, `closedAt`, `title`, `status`, `type`, `key`, `priority` (by severity).

**Workflow**

| From | To | Who |
|---|---|---|
| OPEN | IN_PROGRESS | assignee, PM, admin |
| OPEN | CLOSED | PM, admin |
| IN_PROGRESS | IN_REVIEW, OPEN | assignee, PM, admin |
| IN_REVIEW | IN_PROGRESS | assignee, PM, admin |
| IN_REVIEW | CLOSED | PM, admin |
| CLOSED | OPEN (reopen) | PM, admin; reporter within 14 days |

"PM" means a project manager who owns or belongs to the ticket's project.

## Comments

| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/tickets/{ticketId}/comments` | visible | Oldest first; `canModify` per comment |
| POST | `/tickets/{ticketId}/comments` | anyone who can see the ticket | `{body}` |
| PUT | `/comments/{id}` | author, ADMIN | `{body}`; sets `editedAt` |
| DELETE | `/comments/{id}` | author, ADMIN | Soft delete |

## Dashboard and activity

| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/dashboard?projectId=&weeks=4` | any role | Scope GLOBAL (admin), PROJECTS (PM), PERSONAL (developer: assigned; user: reported). Totals, by status/priority/type, productivity per assignee (closed per week, avg hours to close), 10 recent activities |
| GET | `/activity?projectId=` | any role | Admin: all; PM/dev: their projects + own actions; user: own actions |

## Deployments (Deployment Portal)

| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/deployments/options` | ADMIN | Regions per cloud, environments, which allow APPLY, cluster limits, runner |
| POST | `/deployments` | ADMIN | `{cloudProvider, region, clusterCount 1-5, environment, action? (APPLY/PLAN), confirmEnvironment? ("PROD" for PROD)}` -> 202 |
| GET | `/deployments` | ADMIN | History, newest first |
| GET | `/deployments/{id}` | ADMIN | Deployment + `expectedSteps` + `events` |
| GET | `/deployments/{id}/stream` | ADMIN | Server-Sent Events, event name `deployment`, full detail on each change |
| POST | `/deployments/{id}/destroy` | ADMIN | Finished APPLY only -> DESTROYING -> DESTROYED |
| POST | `/deployments/{id}/events` | pipeline (HMAC) | `{status, message?, runId?, runUrl?}` with `X-TrackFlow-Timestamp` and `X-TrackFlow-Signature: sha256=hex(HMAC_SHA256(secret, timestamp + "." + body))`; rejected if older than 5 min |

Apply steps: `QUEUED -> NETWORK_CREATED -> CLUSTER_CREATED -> DATABASE_CREATED -> BACKEND_DEPLOYED -> FRONTEND_DEPLOYED -> COMPLETED`, or `FAILED` at any point. Plan: `QUEUED -> PLANNED`. Locally the `SIMULATED` runner walks these steps on a timer; on Day 3 the `GITHUB_ACTIONS` runner reports them from the real OpenTofu pipeline.

## Platform (public)

| Path | Purpose |
|---|---|
| `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | Kubernetes probes, Traffic Manager health check |
| `/actuator/info` | App name, cloud, region |
| `/actuator/prometheus` | Metrics |
| `/swagger-ui.html`, `/v3/api-docs` | API docs |
