# Quality, design and performance review

Summary of the four review tasks. Each one has a detailed report, linked below, and the slides are in [presentation/TrackFlow-Quality-Performance-Review.pptx](presentation/TrackFlow-Quality-Performance-Review.pptx).

| Task | Result | Details |
|---|---|---|
| 1. Run Sonar and find all smells | 28 issues (14 bugs, 11 vulnerabilities, 3 code smells) → **0 open**. Ratings reliability C → A, security E → A, maintainability A | [quality/sonar-report.md](quality/sonar-report.md) |
| 2. Design patterns applied | **30 patterns** identified, with the files where each one lives | [design-patterns.md](design-patterns.md) |
| 3. Load test, find the failure point | Healthy up to 340 req/s, **fails at 380 req/s** (p95 17 ms → 1,876 ms, 119 requests dropped) | [performance/load-test-report.md](performance/load-test-report.md) |
| 4. Fix it, raise it by more than 50% | Failure point **380 → 600 req/s (+58%)**, healthy load 340 → 540 req/s (+59%) | [performance/load-test-report.md](performance/load-test-report.md) |

## 1. Sonar

- Scope: the whole repository (12,349 lines of Java, TypeScript, Kubernetes, OpenTofu and Dockerfiles), SonarQube Community with the default "Sonar way" rules. The same settings run in GitHub Actions through SonarCloud.
- Fixed in code (22): 14 unhandled promises in the frontend, a database password and dev JWT key committed in the local Kubernetes overlay (now generated per machine and git-ignored), infrastructure hardening (managed identities, HTTPS-only state bucket, replica backups), and 3 code smells.
- Reviewed and accepted (6): design decisions such as the Basic registry tier being publicly reachable and nip.io demo hosts having no TLS. Each reason is written next to its exclusion in `sonar-project.properties`.
- Open item: frontend test coverage is 15.8% (backend 88.6%).

## 2. Design patterns

Grouped by purpose: architecture (layered, dependency injection, central error handling), behaviour (Strategy for deployment runners, Observer for after-commit events, ticket state machine, access policy, filter chain), data (Repository, Specification, DTO + mapper, records, AOP proxies), reliability and security (token bucket, optimistic locking, auditing, HMAC callbacks, refresh token rotation), frontend (Redux store, RTK Query cache, token-refresh interceptor, route guards) and infrastructure (OpenTofu modules, Kustomize overlays, active-passive failover).

## 3. Load test

- Setup: production image limited to one cloud pod (1 vCPU, 768 MiB), MySQL 8.4 with 2 vCPU and 1 GiB, 3,011 tickets and 9,006 comments, fresh database per run, k6 constant arrival rate, 60 s per step, realistic read and write mix.
- Failure means p95 above 500 ms, 1% or more server errors, or dropped requests.
- Profiling of database time at load: ticket list ~100 s per minute, dashboard ~70 s, autocommit toggling ~37 s, project stats ~28 s.

## 4. Fixes and result

| Change | Why |
|---|---|
| Ticket list as a projection query | Loaded four full entities per row to show 14 fields |
| Dashboard in 1 grouped query instead of 7 | 7 aggregate queries on every view |
| Connections start with autocommit off | 2 extra statements per request |
| 15 s per-viewer dashboard cache | Summary numbers tolerate a few seconds of staleness |

| Load (req/s) | Before p95 | After p95 |
|---|---|---|
| 340 | 17 ms | 6 ms |
| 380 | 1,876 ms (119 dropped) | 7 ms |
| 420 | 3,680 ms (870 dropped) | 7 ms |
| 540 | failed | 6 ms |
| 600 | failed | 2,349 ms (788 dropped) |

All 171 backend tests pass and API responses are unchanged. Server errors were 0 at every step in both runs. Raw results are in `loadtest/results/`.

## Limits and next steps

- One backend pod on a local replica of the cloud resources; the dashboard cache benefits from the small number of test users.
- Next: raise frontend coverage, connect SonarCloud for every push, scale out pods now that the single vCPU is the limit, and add the k6 test to CI as a smoke check.
