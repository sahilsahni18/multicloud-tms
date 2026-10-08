#!/usr/bin/env bash
# Runs TrackFlow on a local kind cluster: http://localhost:8088
#   ./scripts/kind-up.sh              build images and deploy
#   ./scripts/kind-up.sh --skip-build re-apply manifests only
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cluster=trackflow
ingress_version=controller-v1.15.1
skip_build=false
[[ "${1:-}" == "--skip-build" ]] && skip_build=true

step() { printf '\n==> %s\n' "$1"; }

step "Cluster"
if ! kind get clusters | grep -qx "$cluster"; then
  kind create cluster --config "$root/k8s/kind/cluster.yaml"
fi
kubectl config use-context "kind-$cluster" >/dev/null

step "Ingress controller"
kubectl apply -f "https://raw.githubusercontent.com/kubernetes/ingress-nginx/$ingress_version/deploy/static/provider/kind/deploy.yaml" >/dev/null
kubectl wait -n ingress-nginx --for=condition=Ready pod -l app.kubernetes.io/component=controller --timeout=180s

if ! $skip_build; then
  step "Images"
  docker build -t trackflow-backend:local "$root/backend"
  docker build -t trackflow-frontend:local "$root/frontend"
  kind load docker-image trackflow-backend:local trackflow-frontend:local --name "$cluster"
fi

step "Application"
kubectl apply -k "$root/k8s/overlays/local"
if ! $skip_build; then
  kubectl -n trackflow rollout restart deployment/backend deployment/frontend >/dev/null
fi
kubectl -n trackflow rollout status statefulset/mysql --timeout=180s
kubectl -n trackflow rollout status deployment/backend --timeout=300s
kubectl -n trackflow rollout status deployment/frontend --timeout=120s

step "Ready"
kubectl -n trackflow get pods
echo
echo "Open http://localhost:8088  (Swagger: http://localhost:8088/swagger-ui.html)"
echo "Remove everything with: kind delete cluster --name $cluster"
