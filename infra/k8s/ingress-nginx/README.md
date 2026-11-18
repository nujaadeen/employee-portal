# ingress-nginx (vendored)

`deploy-controller-v1.15.1.yaml` is the upstream manifest for kind, unchanged:
https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.15.1/deploy/static/provider/kind/deploy.yaml
(controller v1.15.1, images pinned by digest inside the file).

**Status: ingress-nginx was retired by the Kubernetes project (repository archived on 2026-03-24, no further releases or security fixes).**
It is used here because the task asks for it, for a local HTTP-only cluster on a laptop. It must not be exposed to a network. A copy is kept in
this repository because the upstream repository is archived. To move to another controller (a Gateway API implementation), only the Ingress
objects in `infra/k8s/base/ingress.yaml` and `scripts/kind-up.sh` (install step) change.
