# Infrastructure (OpenTofu)

```
infra/
├── bootstrap/            one-time: state storage + GitHub OIDC roles (run from your laptop)
├── modules/
│   ├── aws/              vpc, eks, ecr, rds, frontend (S3 + CloudFront), route53
│   └── azure/            aks, acr, mysql, static-web-app, traffic-manager
└── envs/dev/
    ├── aws/              us-east-1 + us-west-2 (peered VPCs, EKS x2, one RDS, CloudFront)
    └── azure/            eastasia + koreacentral (AKS x2, one MySQL, Static Web Apps, Traffic Manager)
```

Azure regions are East Asia + Korea Central, not the spec's East US + West Europe:
the Azure for Students subscription only allows uaenorth, eastasia,
indiasouthcentral, koreacentral and malaysiawest. East Asia is the only one of
those with Static Web Apps. AWS keeps us-east-1 + us-west-2.

## How the pieces fit

| Concern | AWS | Azure |
|---|---|---|
| Clusters | EKS in both regions, 1 x t3.medium node each | AKS (Free tier) in both regions, 1 x B2als v2 node each |
| Images | ECR in both regions (CI pushes to both) | One ACR, both clusters pull from it |
| Database | One RDS (db.t4g.micro) in us-east-1; us-west-2 reaches it over VPC peering | One MySQL Flexible (B1ms) in eastasia; koreacentral admitted by its static egress IP |
| Frontend | S3 + CloudFront; `/api/*` forwarded to Traffic Manager (one origin, no CORS) | Static Web Apps (standby copy) |
| Failover | — | Traffic Manager, priority 1–4 across all four clusters, probing `/actuator/health` |
| DNS | Route 53 records only if you own a domain (`domain_name`) | `<name>.trafficmanager.net` |

Free-tier limits are flags, not redesigns: `enable_replicas` (RDS cross-region replica, paid accounts only) and `domain_name` (Route 53). `cluster_count` (1–5) is what the Deployment Portal sets.

## Order of operations

1. **Bootstrap (once, ~US$0).** Creates the state bucket/container and the OIDC trust for GitHub.
   ```bash
   cd infra/bootstrap
   cp terraform.tfvars.example terraform.tfvars   # fill in repo + subscription
   tofu init && tofu apply
   tofu output                                     # values for the next steps
   ```
   Add `AWS_ROLE_ARN`, `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` as GitHub Actions variables.
2. **Configure each stack.** In `envs/dev/aws` and `envs/dev/azure`: copy `backend.hcl.example` → `backend.hcl` and `terraform.tfvars.example` → `terraform.tfvars`. `traffic_manager_dns_name` must be identical in both.
3. **Plan (free, Step 7 exit check).**
   ```bash
   tofu init -backend-config=backend.hcl
   tofu plan
   ```
4. **Apply (costs credits, Step 8 only, at the start of a cloud session).** Creating EKS/AKS and the databases takes 15–25 minutes per region.
5. **Destroy at the end of every session.**
   ```bash
   tofu destroy
   ```
   Then check both consoles for leftover load balancers, disks and public IPs (created by Kubernetes, not by OpenTofu).

## Zero-cost rules

- Nothing is applied before Step 8, and every apply is followed by a destroy the same day.
- Budget alerts at US$5 on both clouds; check both billing pages after every session.
- Never upgrade the AWS account from the Free plan.
- No secrets in Git: state lives in the private bucket/container, `terraform.tfvars` and `backend.hcl` are gitignored, and GitHub reaches the clouds through OIDC.

## Open items for Step 8

- Kustomize `aws` and `azure` overlays (ingress-nginx, image registry, DB URL from `tofu output`).
- Backend `CORS_ALLOWED_ORIGINS`: CloudFront forwards `/api` with the Traffic Manager host, so the CloudFront URL (`app_url` output) must be allowed.
- The Static Web Apps copy calls the API cross-site, where the `SameSite=Strict` refresh cookie is not sent; decide between `SameSite=None` + HTTPS on the API, or treating SWA as a read-only standby.
