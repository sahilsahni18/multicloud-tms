# TrackFlow — Multi-Cloud Ticket Management System

A Jira-style ticket tracker (React + Spring Boot + MySQL) with JWT auth and four roles, packaged in Docker, run on Kubernetes, deployed across two AWS regions and two Azure regions with OpenTofu and GitHub Actions. An admin can also provision a new environment from a one-click Deployment Portal.

## Delivery plan (3 days, cloud last)

Everything is built and proven locally first; the cloud is touched only on Day 3, then destroyed.

| Day | Step | Deliverable | Exit check | Status |
|---|---|---|---|---|
| 1 | 1 | Repo scaffold + MySQL schema (Flyway) + ER diagram | Migrations apply clean on MySQL 8; constraints reject bad data | ✅ |
| 1 | 2 | Spring Boot skeleton + JWT security (register, login, refresh rotation, logout, roles) | Auth integration tests pass (Testcontainers) | ✅ |
| 1 | 3 | Domain APIs: users, projects, tickets (workflow + access policy), comments, history/activity, search, dashboard, deployments API (simulated runner) | Every endpoint in Swagger; service tests green | ✅ |
| 2 | 4 | React shell: login, silent token refresh, protected routes, role-based sidebar | Each role sees the right menu | ✅ |
| 2 | 5 | Pages: dashboard, projects, tickets, ticket detail, users/roles, profile, reports, settings, Deployment Portal + history | Full ticket lifecycle from the UI | ✅ |
| 2 | 6 | Dockerfiles + full Compose stack, Kustomize manifests on kind, GitHub Actions CI | `docker compose up` works; app runs on kind; CI green | ✅ |
| 3 | 7 | OpenTofu modules: AWS (VPC, EKS, ECR, RDS, Route 53, S3 + CloudFront), Azure (RG, AKS, ACR, MySQL Flexible, Traffic Manager, Static Web Apps) | `tofu plan` clean for both clouds | ✅ |
| 3 | 8 | Apply both clouds, CD to 4 clusters, Traffic Manager failover | Killing a region fails over in < 2 min | ✅ failover 38 s (AWS→AWS), 45 s (AWS→Azure) |
| 3 | 9 | Portal wired to the GitHub Actions provisioning workflow; demo rehearsal; `tofu destroy` | Portal deploy lands as COMPLETED in history | ⬜ |

## Repository layout

```
.
├── backend/                Spring Boot 3 (Java 21, Maven wrapper)
│   ├── src/main/java/com/trackflow/tms/
│   │   ├── controller/     REST endpoints (@PreAuthorize on every method)
│   │   ├── service/        business logic, TicketAccessPolicy, workflow, dashboard
│   │   │   └── provisioning/   deployment runners (simulated now, GitHub Actions on Day 3)
│   │   ├── repository/     Spring Data JPA
│   │   ├── entity/         JPA entities + enums
│   │   ├── dto/  mapper/   API shapes
│   │   ├── security/       JWT, filters, rate limit, callback HMAC
│   │   └── config/ exception/ util/
│   └── src/main/resources/db/
│       ├── migration/      Flyway schema (all environments)
│       └── demo/           Flyway demo seed (local/dev/QA only)
├── frontend/               React 19 + TypeScript + Vite
│   └── src/
│       ├── api/            axios client (silent refresh), RTK Query endpoints, DTO types
│       ├── app/            store, hooks, role-based navigation map
│       ├── features/auth/  login, register, session slice, route guards
│       ├── pages/          dashboard, tickets, projects, reports, admin/*, profile, settings
│       └── components/     layout, labels, dialogs, pickers
├── k8s/                    Kustomize base + overlays         (Step 6)
├── infra/                  OpenTofu modules + env stacks     (Step 7)
├── .github/workflows/      CI / CD / provisioning            (Steps 6, 8, 9)
├── docs/                   HLD, LLD, API, diagrams
└── docker-compose.yml      Local stack
```

## Run locally

Requires Docker Desktop and JDK 21 (Maven comes with the wrapper).

```bash
docker compose up -d mysql
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

The backend runs Flyway on start-up (schema + demo data in the `local` profile), then serves:

| URL | What |
|---|---|
| http://localhost:8080/swagger-ui.html | API docs; log in, then click **Authorize** and paste the `accessToken` |
| http://localhost:8080/actuator/health | Health (also `/liveness`, `/readiness`) |
| http://localhost:8080/actuator/prometheus | Metrics |

MySQL listens on `localhost:3306`, database `trackflow`, user `trackflow` / `trackflow_local` (override in `.env`, see `.env.example`). To apply migrations without the backend: `docker compose --profile tools run --rm flyway migrate`.

Then the frontend (Node 20+), in a second terminal:

```bash
cd frontend
npm install
npm run dev                     # http://localhost:5173, proxies /api to localhost:8080
```

Sign in with one of the demo accounts below (the login page lists them in dev mode). To point the dev server at a backend on another port: `VITE_PROXY_TARGET=http://localhost:8081 npm run dev`.

### Tests

```bash
cd backend
./mvnw verify                   # unit + integration tests (Testcontainers starts its own MySQL)

cd frontend
npm run lint && npm test        # ESLint + Vitest / Testing Library
npm run build                   # type-check and production bundle in dist/
```

### Frontend stack

React 19, TypeScript, Vite, React Router, Redux Toolkit + RTK Query, Axios, MUI (restyled in an Atlassian-like style: system fonts, navy text, one blue, issue-type and priority icons, status lozenges, initials avatars), Vitest + Testing Library, ESLint. The Board page is a Kanban view per project: drag a card to another column to move the ticket through the workflow (the API rejects moves your role may not make). The access token is kept in memory only; the refresh token is an httpOnly cookie, and a 401 triggers one shared refresh call before the request is retried. Menus and routes come from one role map (`src/app/navigation.ts`); the API enforces the same rules.

Coverage report: `backend/target/site/jacoco/index.html`.

### Backend configuration

| Env var | Default | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | - | `local` on a laptop, `cloud` on EKS/AKS |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | localhost / trackflow | |
| `JWT_SECRET` | dev key in `local` only | Base64, at least 64 bytes: `openssl rand -base64 64` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` in `local` | Comma-separated frontend origins |
| `REFRESH_COOKIE_SECURE`, `REFRESH_COOKIE_SAME_SITE` | `true`, `Strict` | |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | unset | Creates the first admin where there is no demo data |
| `AUTH_RATE_LIMIT_PER_MINUTE` | 10 | Login/register/refresh per IP |
| `DEPLOY_RUNNER` | `SIMULATED` | `GITHUB_ACTIONS` once the provisioning workflow exists (Day 3) |
| `DEPLOY_SIMULATED_STEP_DELAY` | `3s` | Pause between simulated portal steps |
| `DEPLOY_CALLBACK_SECRET` | unset | HMAC key shared with the provisioning workflow; unset disables callbacks |
| `DEPLOY_APPLY_ENVIRONMENTS` | `DEV` | Environments allowed to APPLY; others are PLAN-only |

Demo users (password `Password@123`):

| Email | Role |
|---|---|
| admin@trackflow.dev | ADMIN |
| pm@trackflow.dev | PROJECT_MANAGER |
| dev@trackflow.dev, dev2@trackflow.dev | DEVELOPER |
| user@trackflow.dev | USER |

## Docs

- [API reference](docs/api/api-reference.md) (interactive: `/swagger-ui.html`)
- [Database design & ER diagram](docs/database/er-diagram.md)
- [Security flow](docs/architecture/security-flow.md)
