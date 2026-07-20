# Minimal Kubernetes manifests (pulled forward from E6.4)

These exist so E6.3b (KEDA) and E6.3c (HPA) have something to scale — they are
deliberately the bare minimum, not the finished deployment story:

- plain `Deployment` + `Service` per component, no Helm chart
- `emptyDir` instead of `PersistentVolumeClaim`, so **all data is lost on pod
  restart** — acceptable for a scaling demo, not for anything else
- no Ingress, no resource tuning beyond what the CPU-based HPA requires

`autoscaling/` is a **separate directory on purpose**: `kubectl apply -f .` on this
directory must not install both scalers at once. See `autoscaling/README-scope.md`.
