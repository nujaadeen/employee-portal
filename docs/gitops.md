# CI and Argo CD (GitOps on the laptop)

## GitHub Actions (`.github/workflows/`)
All actions are pinned by commit SHA.

| Workflow | When | What |
|---|---|---|
| `ci.yml` | every PR and push to main | backend `mvn verify`, frontend lint/test/build, ai-service ruff/mypy/pytest, `helm lint` (3 value sets, schema must refuse bad values), kubeconform, `bash -n` on scripts, and a check that `docs/openapi.json` and the generated API types are committed |
| `images.yml` | push to main or tag `v*`, when app code changes | builds backend, frontend, ai-service, keycloak images and pushes `ghcr.io/<owner>/baracel-portal-<name>:{sha,latest,tag}` |
| `chart.yml` | PRs touching the chart or scripts, push to main, tag `chart-vX.Y.Z` | lint and package; **installs the chart into a kind cluster** with `scripts/kind-up.sh` (fake LLM and embeddings) and runs `helm test` and `scripts/kind-smoke.sh`; on a tag it pushes the chart to `oci://ghcr.io/<owner>/charts` |

Publish a chart: bump `version` in `infra/helm/baracel-portal/Chart.yaml`, merge, then `git tag chart-v0.1.1 && git push origin chart-v0.1.1`. The tag must equal the Chart.yaml version or the job fails.

Not verified yet: these workflows have never run on GitHub (no remote is configured in this repository). Their YAML parses and the chart commands were run locally; the first run may need small fixes. The kind job uses fake embeddings, so the smoke test's access check runs against the fake embedder.

## Argo CD in kind (local)
GitHub-hosted runners cannot reach your laptop's cluster, so the Argo CD part runs locally and pulls the chart from a registry **inside** the cluster (the stand-in for ghcr.io).

```
scripts/kind-argocd.sh install     # Argo CD v3.6.1 (no Dex, no notifications), local OCI chart registry, project, repository
scripts/kind-argocd.sh push        # helm package + helm push of infra/helm/baracel-portal
scripts/kind-argocd.sh adopt       # hand a release installed with helm over to Argo CD (pods are not restarted)
scripts/kind-argocd.sh deploy      # create or update the Application (use --mode local|public)
scripts/kind-argocd.sh status | ui
```
Manifests: `infra/argocd/` (registry, AppProject `baracel`, repository Secret, Application template). The Application has automated sync with prune and self-heal: a manual `kubectl scale` is reverted within seconds (verified). Secrets are not in Git or in the Application; they stay in the cluster (`kind-secrets.sh`). After `adopt`, `scripts/kind-public.sh up|local` updates the Application instead of running `helm upgrade`.

Change flow: edit the chart, bump the version, `push`, `deploy`.

To use the chart from GitHub instead, point the Application at `ghcr.io/<owner>/charts` (repoURL without `oci://`) and add a repository Secret with a read token if the package is private.

After a reboot or new session: `scripts/kind-host-services.sh start` (Ollama on the kind gateway and the ngrok tunnel).
