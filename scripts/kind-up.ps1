<#
.SYNOPSIS
  Runs TrackFlow on a local kind cluster: http://localhost:8088

.DESCRIPTION
  Creates the cluster (once), installs the NGINX ingress controller, builds
  both images, loads them into kind and applies k8s/overlays/local.
  Re-run it after code changes; use -SkipBuild to only re-apply manifests.

.EXAMPLE
  ./scripts/kind-up.ps1
  ./scripts/kind-up.ps1 -SkipBuild
#>
param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$cluster = 'trackflow'
$ingressVersion = 'controller-v1.15.1'

function Step($text) { Write-Host "`n==> $text" -ForegroundColor Cyan }
function Check() { if ($LASTEXITCODE -ne 0) { throw "Command failed (exit $LASTEXITCODE)" } }

Step 'Cluster'
if (-not ((kind get clusters) -contains $cluster)) {
    kind create cluster --config "$root/k8s/kind/cluster.yaml"; Check
} else {
    Write-Host "kind cluster '$cluster' already exists"
}
kubectl config use-context "kind-$cluster" | Out-Null; Check

Step 'Ingress controller'
kubectl apply -f "https://raw.githubusercontent.com/kubernetes/ingress-nginx/$ingressVersion/deploy/static/provider/kind/deploy.yaml" | Out-Null; Check
kubectl wait -n ingress-nginx --for=condition=Ready pod -l app.kubernetes.io/component=controller --timeout=180s; Check

if (-not $SkipBuild) {
    Step 'Images'
    docker build -t trackflow-backend:local "$root/backend"; Check
    docker build -t trackflow-frontend:local "$root/frontend"; Check
    kind load docker-image trackflow-backend:local trackflow-frontend:local --name $cluster; Check
}

Step 'Application'
kubectl apply -k "$root/k8s/overlays/local"; Check
if (-not $SkipBuild) {
    # Same tag, new image: restart so the pods pick it up.
    kubectl -n trackflow rollout restart deployment/backend deployment/frontend | Out-Null; Check
}
kubectl -n trackflow rollout status statefulset/mysql --timeout=180s; Check
kubectl -n trackflow rollout status deployment/backend --timeout=300s; Check
kubectl -n trackflow rollout status deployment/frontend --timeout=120s; Check

Step 'Ready'
kubectl -n trackflow get pods
Write-Host "`nOpen http://localhost:8088  (Swagger: http://localhost:8088/swagger-ui.html)" -ForegroundColor Green
Write-Host "Remove everything with: kind delete cluster --name $cluster"
