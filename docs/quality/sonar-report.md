# Static analysis report (SonarQube)

**Scope:** the whole repository, 12,349 lines of code: Java backend, TypeScript frontend, Kubernetes manifests, OpenTofu (Terraform) and Dockerfiles.
**Tool:** SonarQube Community 26.10 with the default "Sonar way" quality profile, run with `sonar-scanner` and the settings in [`sonar-project.properties`](../../sonar-project.properties). The same settings are used by the SonarCloud workflow ([`.github/workflows/sonar.yml`](../../.github/workflows/sonar.yml)).

## Result

| Metric | First scan | After fixes |
|---|---|---|
| Bugs | 14 | **0** |
| Vulnerabilities | 11 | **0** |
| Code smells | 3 | **0** |
| Security hotspots to review | 0 | **0** |
| Reliability rating | C | **A** |
| Security rating | E | **A** |
| Maintainability rating | A | **A** |
| Duplicated lines | 0.3% | 0.3% |
| Coverage, backend | 88.6% | 88.6% |
| Coverage, frontend | 0% (not measured) | 15.8% (now measured) |
| Coverage, overall | 39.5% | 39.6% |

## What was found and how it was fixed

### Bugs (14 → 0)

| Rule | Where | Finding | Fix |
|---|---|---|---|
| typescript:S9383 (×14) | `AppLayout.tsx` (3), `BoardPage.tsx`, `Timeline.tsx`, `LoginPage.tsx`, `RegisterPage.tsx`, `main.tsx`, `ProfilePage.tsx`, `DeploymentsPage.tsx`, `ProjectDetailPage.tsx`, `ProjectsPage.tsx`, `NewTicketDialog.tsx`, `TicketDetailPage.tsx` | Promises returned by `navigate(...)`, RTK mutations and thunks were neither awaited nor explicitly ignored | Marked as intentional fire-and-forget with `void`. Each call either cannot fail in a way the caller can handle (navigation) or already reports its own errors (`drop` in `BoardPage` catches and shows a toast; RTK mutations resolve with an error object) |

### Code smells (3 → 0)

| Rule | Where | Finding | Fix |
|---|---|---|---|
| java:S1845 (Blocker) | `util/IpRateLimiter.java` | Constant `ALLOWED` and record field `allowed` differ only in case | Renamed the constant to `PERMIT` |
| java:S8786 | `service/provisioning/GitHubActionsProvisioningRunner.java` | Regular expression `/+$` can backtrack (super-linear time) | Replaced with a linear `stripTrailingSlashes` loop |
| typescript:S7767 | `components/UserAvatar.tsx` | Integer truncation with `| 0` | Uses `Math.trunc` |

### Vulnerabilities (11 → 0)

**Fixed in code (5):**

| Rule | Where | Finding | Fix |
|---|---|---|---|
| secrets:S6697 (Blocker) | `k8s/overlays/local/kustomization.yaml` | MySQL password committed in the local kind overlay (the dev JWT key next to it as well) | Secrets now come from git-ignored `*.env` files that `scripts/kind-up.sh` / `.ps1` generate with random values; `*.env.example` files document the format and CI renders the overlay with them |
| terraform:S6378 | `infra/modules/azure/acr/main.tf` | Container registry without a managed identity | Added a system-assigned identity |
| terraform:S6378 | `infra/bootstrap/main.tf` | State storage account without a managed identity | Added a system-assigned identity |
| terraform:S6249 | `infra/bootstrap/main.tf` | S3 state bucket accepted plain-HTTP requests | Bucket policy that denies any request without TLS (`aws:SecureTransport = false`) |
| terraform:S6364 | `infra/envs/dev/aws/main.tf` | Cross-region RDS read replica (optional, `enable_replicas`) had no backup retention | `backup_retention_period = 7` |

**Reviewed and accepted on purpose (6).** These are "make sure this is safe" findings, not defects. Each was reviewed, and the decision is recorded next to the exclusion in `sonar-project.properties` so it applies to every scan:

| Rule | Where | Why it is accepted |
|---|---|---|
| terraform:S6329 (Blocker) | `infra/modules/azure/acr/main.tf` | Basic and Standard Azure Container Registries cannot use private endpoints. Access is controlled by identity instead: CI pushes with GitHub OIDC, AKS pulls with its kubelet identity, the admin user is disabled. Now set explicitly (`public_network_access_enabled = true`) with a comment |
| terraform:S6387 | `infra/bootstrap/main.tf` | The pipeline identity creates the resource groups itself, so its Contributor role needs subscription scope. It is trusted only for this repository's `main` branch and its environments |
| terraform:S6258 | `infra/bootstrap/main.tf` | Access logging on the state bucket would need a second bucket to hold logs for a few KB of state. The bucket is private, versioned, encrypted and now TLS-only |
| terraform:S6258 (×2) | `infra/modules/aws/frontend/main.tf` | The S3 + CloudFront frontend is behind `enable_cloudfront`, which is off (new AWS accounts cannot create CloudFront distributions). Access logs should be enabled together with it |
| kubernetes:S5332 | `k8s/overlays/portal/env-patch.yaml` | Deployment Portal demo environments are served on `nip.io` hostnames, which have no TLS certificate |

## Quality gate

The default gate ("Sonar way") requires 80% coverage on new code. With everything counted as new code on a first scan, the gate fails on coverage only: **41.4%** against 80%. All other conditions pass (0 new issues, 0% new duplication).

The cause is the frontend: **15.8%** line coverage (19 tests on the login page, route guards, auth slice, navigation and deployment steps), while the backend has **88.6%** (98 unit and integration tests). On SonarCloud the gate only judges code changed after the first analysis, so it passes as long as new code is tested; the frontend test gap is still the main open quality item.

## Open items

1. **Frontend tests**: add component tests for the ticket pages, board, dashboard and admin pages to move frontend coverage towards the backend's level.
2. **Access logging**: enable S3 and CloudFront access logs together with `enable_cloudfront`.

## How to reproduce

```bash
# coverage reports
cd backend && ./mvnw verify && cd ..
cd frontend && npm ci && npx vitest run --coverage && cd ..

# local SonarQube
docker run -d --name sonarqube -p 9000:9000 sonarqube:community
# create a token in the UI (My Account > Security), then:
docker run --rm -e SONAR_HOST_URL=http://host.docker.internal:9000 -e SONAR_TOKEN=<token> \
  -v "$PWD:/usr/src" sonarsource/sonar-scanner-cli
```

On GitHub, the **SonarCloud** workflow runs the same analysis on every push once the `SONAR_TOKEN` secret is set.
