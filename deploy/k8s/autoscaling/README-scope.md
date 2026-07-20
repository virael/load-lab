# Two scalers, never both at once

`worker-scaledobject.yaml` (KEDA, queue lag) and `worker-hpa-cpu.yaml` (plain HPA,
CPU) both write `spec.replicas` on the **same** `worker` Deployment. Applied
together they fight: each reverts the other's decision, and the replica count
oscillates. KEDA also generates its own HPA under the hood, so this is literally
two HPAs on one target.

That is why these live outside `deploy/k8s/` — `kubectl apply -f deploy/k8s/` must
not sweep them both in. Apply exactly one, explicitly:

    kubectl apply -f deploy/k8s/autoscaling/worker-scaledobject.yaml   # the real one
    kubectl apply -f deploy/k8s/autoscaling/worker-hpa-cpu.yaml        # the counterexample

`worker-hpa-cpu.yaml` is kept as a documented counterexample, not as a fallback.
The reason it is the wrong signal is in the comments inside that file.
