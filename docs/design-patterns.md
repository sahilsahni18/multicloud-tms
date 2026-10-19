# Design patterns in TrackFlow

Each pattern below is used on purpose, with the file where it lives and the problem it solves. Paths are relative to `backend/src/main/java/com/trackflow/tms/` unless they start with `frontend/`, `infra/`, `k8s/` or `.github/`.

## Summary

| # | Pattern | Category | Where |
|---|---|---|---|
| 1 | Layered architecture (Controller, Service, Repository) | Architectural | `controller/`, `service/`, `repository/` |
| 2 | Repository | Data access | `repository/*Repository.java` |
| 3 | Specification | Data access | `service/TicketSpecifications.java` |
| 4 | Data Transfer Object + Mapper | Structural | `dto/`, `mapper/` (MapStruct) |
| 5 | Strategy | Behavioural | `service/provisioning/ProvisioningRunner.java` and its implementations |
| 6 | Registry / dispatcher over strategies | Behavioural | `service/provisioning/ProvisioningDispatcher.java` |
| 7 | Observer (domain events) | Behavioural | `service/provisioning/DeploymentEvents.java`, `@TransactionalEventListener` |
| 8 | Publish/subscribe to clients (Server-Sent Events) | Behavioural | `service/DeploymentStreamService.java` |
| 9 | State machine | Behavioural | `entity/TicketStatus.java` |
| 10 | Policy (access rules in one place) | Behavioural | `service/TicketAccessPolicy.java`, `service/ProjectAccessPolicy.java` |
| 11 | Chain of Responsibility (servlet filters) | Behavioural | `config/CorrelationIdFilter.java`, `security/JwtAuthenticationFilter.java`, `security/AuthRateLimitFilter.java` |
| 12 | Proxy / AOP (declarative cross-cutting concerns) | Structural | `@Transactional`, `@PreAuthorize`, `@EnableMethodSecurity` |
| 13 | Template Method | Behavioural | `OncePerRequestFilter` subclasses, `exception/GlobalExceptionHandler.java` |
| 14 | Centralised error handling (Problem Details) | Architectural | `exception/GlobalExceptionHandler.java`, `exception/*Exception.java` |
| 15 | Static factory method | Creational | `service/provisioning/DeploymentRun.of(...)`, `NotFoundException.of(...)` |
| 16 | Builder | Creational | `GitHubActionsProvisioningRunner` (`RestClient.builder()`), `JwtService` (JJWT builder), `IpRateLimiter` (`Bucket.builder()`) |
| 17 | Dependency Injection / Inversion of Control | Architectural | Every `@Component`, `@Service`, constructor injection |
| 18 | Immutable value objects | Structural | Java `record`s in `dto/`, `DeploymentRun`, `DeploymentProperties` |
| 19 | Token bucket (rate limiting) | Resilience | `util/IpRateLimiter.java` |
| 20 | Optimistic locking | Concurrency | `@Version` in `entity/Ticket.java`, `Project.java`, `Comment.java`, `Deployment.java`, `User.java` |
| 21 | Auditing via entity listener | Cross-cutting | `entity/AuditableEntity.java`, `config/JpaConfig.java` (`AuditorAware`) |
| 22 | HMAC-signed callback (message authentication) | Security | `security/DeploymentCallbackVerifier.java`, `scripts/report-deployment.sh` |
| 23 | Refresh token rotation | Security | `service/RefreshTokenService.java`, `security/RefreshCookieManager.java` |
| 24 | Flux / single store (Redux Toolkit) | Frontend | `frontend/src/app/store.ts`, `frontend/src/features/auth/authSlice.ts` |
| 25 | Data-fetching cache (RTK Query) | Frontend | `frontend/src/api/api.ts` |
| 26 | Interceptor (silent token refresh) | Frontend | `frontend/src/api/http.ts` |
| 27 | Route guard / higher-order component | Frontend | `frontend/src/features/auth/guards.tsx`, `frontend/src/app/navigation.ts` |
| 28 | Infrastructure as code with reusable modules | Infrastructure | `infra/modules/*`, `infra/envs/dev/*` |
| 29 | Overlay configuration (base + per-environment patches) | Infrastructure | `k8s/base`, `k8s/overlays/{local,cloud,portal}` |
| 30 | Active-passive failover with health checks | Infrastructure | `infra/modules/azure/traffic-manager`, `/actuator/health` probes |

---

## Backend

### 1. Layered architecture
**Where:** `controller/` → `service/` → `repository/`.
Controllers only translate HTTP to method calls (and carry `@PreAuthorize`), services hold business rules and transactions, repositories talk to the database. Each layer depends only on the one below, so the ticket workflow can be unit-tested without HTTP or a database.

### 2. Repository
**Where:** `repository/TicketRepository.java` and the other `*Repository` interfaces (Spring Data JPA).
The rest of the code asks for domain objects (`findByIdForUpdate`, `findPage`) instead of writing SQL, which keeps persistence details out of the services.

### 3. Specification
**Where:** `service/TicketSpecifications.java`.
Ticket search combines optional filters (project, status, priority, assignee, text, ticket key such as `TMS-42`). Each filter is a small JPA `Specification`, and they are combined with `and(...)` only when the filter is present. This avoids one query method per filter combination.

### 4. Data Transfer Object + Mapper
**Where:** `dto/` (36 request and response records), `mapper/*Mapper.java` (MapStruct, compile-time generated).
The API never exposes JPA entities: requests and responses are separate records, and MapStruct converts between them. This keeps the API stable when the database changes and prevents accidental exposure of fields such as password hashes.

### 5. Strategy
**Where:** `service/provisioning/ProvisioningRunner.java` (interface) with `SimulatedProvisioningRunner` and `GitHubActionsProvisioningRunner`.
The Deployment Portal needs "run this deployment somewhere". Locally it walks through the steps on a timer; in the cloud it starts a GitHub Actions workflow. Both implement one interface, and the choice is configuration (`DEPLOY_RUNNER`), not code.

### 6. Registry / dispatcher over strategies
**Where:** `service/provisioning/ProvisioningDispatcher.java`.
Spring injects every `ProvisioningRunner`; the dispatcher stores them in an `EnumMap<RunnerType, ProvisioningRunner>` and picks the one recorded on the deployment. Adding a new runner (for example a local OpenTofu runner) is a new class, with no change to existing code (open/closed principle).

### 7. Observer (domain events)
**Where:** `service/provisioning/DeploymentEvents.java` (`StartRequested`, `Changed`), published by `DeploymentService`, handled with `@TransactionalEventListener` in `ProvisioningDispatcher` and `DeploymentStreamService`.
The service that saves a deployment does not know who reacts to it. Listeners run **after the transaction commits**, so a runner never starts work for a deployment that might still roll back.

### 8. Publish/subscribe to clients (Server-Sent Events)
**Where:** `service/DeploymentStreamService.java`.
Browsers subscribe to `/api/v1/deployments/{id}/stream`; the service keeps a set of `SseEmitter`s per deployment and pushes an update whenever a `Changed` event arrives.

### 9. State machine
**Where:** `entity/TicketStatus.java` (`allowedNext()`, `canMoveTo(...)`).
Each status lists its legal next statuses (OPEN → IN_PROGRESS or CLOSED, IN_PROGRESS → IN_REVIEW or OPEN, IN_REVIEW → CLOSED or IN_PROGRESS, CLOSED → OPEN). Every transition goes through this table, and illegal moves become **409 Conflict**.

### 10. Policy
**Where:** `service/TicketAccessPolicy.java`, `service/ProjectAccessPolicy.java`.
Row-level rules ("own ticket", "assigned to me", "member of this project", "reopen within 14 days") live in one class each instead of being repeated across controllers, so they can be unit-tested directly.

### 11. Chain of Responsibility
**Where:** `config/CorrelationIdFilter.java`, `security/AuthRateLimitFilter.java`, `security/JwtAuthenticationFilter.java`, assembled in `security/SecurityConfig.java`.
Every request passes through a chain: correlation id for logging, rate limit for auth endpoints, JWT validation, then authorization. Each filter does one job and either passes the request on or stops it.

### 12. Proxy / AOP
**Where:** `@Transactional` on services, `@PreAuthorize` on controllers, `@EnableMethodSecurity`.
Spring wraps these beans in proxies, so transactions and role checks are declared once per method instead of being coded into each one.

### 13. Template Method
**Where:** the three filters above extend `OncePerRequestFilter` and only implement `doFilterInternal`; `exception/GlobalExceptionHandler.java` extends `ResponseEntityExceptionHandler` and overrides only the hooks it needs.

### 14. Centralised error handling
**Where:** `exception/GlobalExceptionHandler.java`, `exception/ApiException.java` and its subclasses (`NotFoundException`, `ConflictException`, ...).
All errors leave the API in one format (RFC 7807 problem+json with a correlation id), whatever layer raised them.

### 15. Static factory method
**Where:** `DeploymentRun.of(Deployment)`, `NotFoundException.of(entity, id)`.
Named constructors that make intent clear and hide how the object is assembled.

### 16. Builder
**Where:** `RestClient.builder()` in `GitHubActionsProvisioningRunner`, JJWT's builder in `JwtService`, `Bucket.builder()` in `IpRateLimiter`.

### 17. Dependency Injection
**Where:** everywhere, through constructor injection (Lombok `@RequiredArgsConstructor`).
Dependencies such as `Clock` are injected rather than created, which is why tests can move time with `MutableClock`.

### 18. Immutable value objects
**Where:** Java `record`s for DTOs, `DeploymentRun` ("safe to hand to another thread"), configuration records such as `DeploymentProperties`.

### 19. Token bucket
**Where:** `util/IpRateLimiter.java` (Bucket4j).
Login, register and refresh allow 10 requests per minute per IP, refilled continuously, to slow down password guessing.

### 20. Optimistic locking
**Where:** `@Version` fields on `Ticket`, `Project`, `Comment`, `Deployment`, `User`.
Two people editing the same ticket cannot silently overwrite each other; the second save fails with a conflict instead of holding database locks.

### 21. Auditing
**Where:** `entity/AuditableEntity.java` (`@EntityListeners(AuditingEntityListener.class)`), `config/JpaConfig.java` (`AuditorAware`).
`created_at`, `updated_at`, `created_by` and `updated_by` are filled automatically on every table.

### 22. HMAC-signed callback
**Where:** `security/DeploymentCallbackVerifier.java` and `scripts/report-deployment.sh`.
The provisioning pipeline signs `timestamp.body` with a shared secret; the backend recomputes the signature, compares in constant time and rejects old timestamps (replay protection).

### 23. Refresh token rotation
**Where:** `service/RefreshTokenService.java`, `security/RefreshCookieManager.java`, `util/TokenHasher.java`.
Each refresh issues a new token and revokes the old one; only hashes are stored, so a database leak does not expose usable tokens.

## Frontend

### 24. Flux / single store
**Where:** `frontend/src/app/store.ts`, `frontend/src/features/auth/authSlice.ts`.
Session state lives in one Redux store and changes only through actions (`login`, `restoreSession`), which makes the login flow predictable and testable (`authSlice.test.ts`).

### 25. Data-fetching cache
**Where:** `frontend/src/api/api.ts` (RTK Query `createApi`).
Endpoints declare what they provide and invalidate (tags), so creating a ticket automatically refreshes the ticket list and the board without manual refetching.

### 26. Interceptor
**Where:** `frontend/src/api/http.ts` (Axios request and response interceptors).
The request interceptor adds the access token; the response interceptor catches a 401, makes **one shared** refresh call for all waiting requests and retries them.

### 27. Route guard
**Where:** `frontend/src/features/auth/guards.tsx`, `frontend/src/app/navigation.ts`.
`RequireAuth`, `RequireRole` and `GuestOnly` wrap pages, and menus and routes come from one role map, so each role sees only what it may use (the API enforces the same rules).

## Infrastructure and delivery

### 28. Reusable modules
**Where:** `infra/modules/aws/*`, `infra/modules/azure/*`, composed by `infra/envs/dev/{aws,azure}`.
Each cloud building block (VPC, EKS, RDS, AKS, Traffic Manager, ...) is a module with inputs and outputs; environments only wire modules together. `cluster_count` is a `for_each`, and paid features are boolean flags.

### 29. Overlays
**Where:** `k8s/base`, `k8s/frontend`, `k8s/overlays/local`, `k8s/overlays/cloud`, `k8s/overlays/portal`.
One base set of manifests, with small Kustomize patches per environment, so the same images run on kind, EKS, AKS and in portal environments.

### 30. Active-passive failover
**Where:** `infra/modules/azure/traffic-manager`, health endpoints `/actuator/health/{liveness,readiness}`.
Traffic Manager sends all traffic to the highest-priority healthy cluster and moves it when health checks fail (measured 38 s between AWS regions and 45 s from AWS to Azure).
