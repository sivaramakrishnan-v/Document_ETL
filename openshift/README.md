# DocumentETL OpenShift Interview Lab

Goal: learn the OpenShift concepts most commonly asked in interviews using this Spring Boot app. This is a lightweight lab, not a production architecture.

Target time: under 2 hours after you have access to any OpenShift cluster, such as Red Hat Developer Sandbox, OpenShift Local, or a classroom cluster.

## Project Folder Structure

```text
DocumentETL/
  Dockerfile
  pom.xml
  src/
  openshift/
    configmap.yaml
    secret.example.yaml
    secret.yaml
    deployment.yaml
    service.yaml
    route.yaml
    kustomization.yaml
    README.md
```

`secret.yaml` is ignored by Git. Keep real passwords and service-account JSON there only for local practice.

## Concept Mapping

| Local Container Concept | OpenShift Concept | Interview Explanation |
| --- | --- | --- |
| Podman image | Container image | Immutable package containing the app and runtime. |
| Podman container | Pod | OpenShift runs containers inside Pods. The Pod is the smallest schedulable unit. |
| `-p 8080:8080` port mapping | Service + Route | Service gives stable internal access. Route exposes HTTP/HTTPS outside the cluster. |
| `-e NAME=value` | ConfigMap or Secret | ConfigMap for normal config. Secret for sensitive config. |
| One container run | Deployment replica | A Deployment manages one or more identical Pods. |
| Multiple containers manually | Replicas | Scaling replicas creates multiple Pods from the same template. |
| Restart container | Rollout restart | OpenShift recreates Pods through the Deployment controller. |

## Dockerfile

The root `Dockerfile` is a multi-stage build:

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Interview points:

- Maven exists only in the build stage.
- Final image contains the JRE and packaged JAR.
- `EXPOSE 8080` documents the app port; it does not publish the port by itself.
- `USER spring:spring` avoids running the app as root.

## Deployment YAML

File: `openshift/deployment.yaml`

What it teaches:

- A Deployment manages Pods.
- `replicas: 1` means one running Pod.
- `envFrom` loads normal environment variables from a ConfigMap.
- `secretKeyRef` loads sensitive values from a Secret.
- `volumeMounts` mounts the GCP JSON from a Secret as a file.
- Readiness decides whether traffic should be sent to the Pod.
- Liveness decides whether the container should be restarted.

## Service YAML

File: `openshift/service.yaml`

What it teaches:

- A Service gives stable internal networking for changing Pods.
- Pods are temporary; Service DNS remains stable.
- `selector.app: documentetl` finds Pods with label `app: documentetl`.
- `type: ClusterIP` means internal-only access.

Internal DNS name:

```text
documentetl.<project>.svc.cluster.local
```

Short name from same project:

```text
documentetl
```

## Route YAML

File: `openshift/route.yaml`

What it teaches:

- Route is OpenShift's standard external HTTP/HTTPS exposure mechanism.
- Route points to a Service, not directly to a Pod.
- `termination: edge` means TLS terminates at the OpenShift router.

Interview shortcut:

```text
Pod -> Service -> Route -> Browser
```

## ConfigMap YAML

File: `openshift/configmap.yaml`

What it teaches:

- ConfigMap stores non-sensitive configuration.
- These keys map to placeholders in `src/main/resources/application.properties`.
- In OpenShift, service names replace `localhost`.

Example:

```yaml
SPRING_DATASOURCE_URL: jdbc:postgresql://pgvector-db:5432/ai_advisor_db?currentSchema=document_etl,public
```

This overrides:

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/ai_advisor_db?currentSchema=document_etl,public}
```

Interview answer:

```text
application.properties defines defaults. ConfigMap provides environment-specific runtime values.
```

## Secret YAML

Files:

- `openshift/secret.example.yaml`
- `openshift/secret.yaml`

What it teaches:

- Secret stores sensitive values.
- Secret values are base64-encoded in Kubernetes storage, not automatically encrypted unless the cluster enables encryption at rest.
- Do not commit real `secret.yaml`.
- Secrets can be injected as environment variables or mounted as files.

This lab uses both:

- DB password as an environment variable.
- GCP JSON as a mounted file.

## Prepare The Lab

Install CLI:

```powershell
oc version
podman version
```

Login:

```powershell
oc login --token=<token> --server=<api-url>
oc whoami
```

Create or select project:

```powershell
oc new-project documentetl
```

If it already exists:

```powershell
oc project documentetl
```

Prepare local secret:

```powershell
Copy-Item openshift\secret.example.yaml openshift\secret.yaml -Force
notepad openshift\secret.yaml
```

## Containerize With Podman

Build:

```powershell
podman build -t documentetl:latest .
```

Verify:

```powershell
podman images documentetl
```

Optional local run:

```powershell
podman run --rm --name documentetl-test -p 8080:8080 documentetl:latest
```

Expected local issue:

```text
Connection to localhost:5432 refused
```

Interview explanation:

```text
localhost inside a container means the container itself, not the host and not another container.
```

## Push Image To OpenShift Registry

Enable registry route if your lab cluster allows it:

```powershell
oc patch configs.imageregistry.operator.openshift.io/cluster --patch '{"spec":{"defaultRoute":true}}' --type=merge
```

Get registry host:

```powershell
$REGISTRY = oc get route default-route -n openshift-image-registry --template='{{ .spec.host }}'
echo $REGISTRY
```

Login:

```powershell
podman login -u $(oc whoami) -p $(oc whoami -t) $REGISTRY
```

Tag:

```powershell
podman tag documentetl:latest "$REGISTRY/documentetl/documentetl:latest"
```

Push:

```powershell
podman push "$REGISTRY/documentetl/documentetl:latest"
```

Interview explanation:

```text
OpenShift nodes cannot pull from my laptop's local Podman image store. The image must be in a registry.
```

## Apply Resources

Preview generated YAML:

```powershell
oc kustomize openshift
```

Apply:

```powershell
oc apply -k openshift
```

Verify resources:

```powershell
oc get deployment
oc get pods
oc get service
oc get route
oc get configmap
oc get secret
```

Wait for rollout:

```powershell
oc rollout status deployment/documentetl
```

Watch logs:

```powershell
oc logs deployment/documentetl -f
```

Get URL:

```powershell
oc get route documentetl
```

## Scaling Replicas

Scale to 2 Pods:

```powershell
oc scale deployment/documentetl --replicas=2
oc get pods -l app=documentetl
```

Scale back:

```powershell
oc scale deployment/documentetl --replicas=1
```

Interview explanation:

```text
Replicas increase the number of Pods created from the same Deployment template. A Service load-balances traffic across ready Pods.
```

## Rolling Deployment

Change image:

```powershell
oc set image deployment/documentetl documentetl=image-registry.openshift-image-registry.svc:5000/documentetl/documentetl:v2
oc rollout status deployment/documentetl
```

View rollout history:

```powershell
oc rollout history deployment/documentetl
```

Rollback:

```powershell
oc rollout undo deployment/documentetl
```

Interview explanation:

```text
During a rolling update, the Deployment controller gradually creates new Pods and terminates old Pods while maintaining availability when readiness probes pass.
```

## Rollout Restart

Restart Pods without changing the image:

```powershell
oc rollout restart deployment/documentetl
oc rollout status deployment/documentetl
```

Use this after ConfigMap or Secret changes:

```powershell
oc apply -k openshift
oc rollout restart deployment/documentetl
```

Interview explanation:

```text
Environment variables from ConfigMaps and Secrets are read when the container starts. Existing Pods do not automatically refresh those environment variables.
```

## Commands To Verify Resources

```powershell
oc describe deployment documentetl
oc describe pod <pod-name>
oc get pods -o wide
oc logs <pod-name>
oc get endpoints documentetl
oc get route documentetl -o yaml
oc get events --sort-by=.lastTimestamp
```

## Interview Questions And Answers

### OpenShift vs Kubernetes

OpenShift is an enterprise Kubernetes distribution. It includes Kubernetes plus opinionated defaults and integrated features such as Routes, image streams, build configs, stricter security defaults, OAuth integration, registry integration, and developer tooling.

### Route vs Ingress

Ingress is the Kubernetes standard API for HTTP routing. Route is OpenShift's native HTTP/HTTPS exposure object. In OpenShift, Routes are widely used because they integrate directly with the OpenShift router and support features like edge TLS termination.

### Deployment vs DeploymentConfig

Deployment is the standard Kubernetes workload controller. DeploymentConfig is an older OpenShift-specific controller with features like image-change triggers and lifecycle hooks. For modern Kubernetes portability, prefer Deployment unless maintaining older OpenShift apps.

### ConfigMap vs Secret

ConfigMap stores non-sensitive config. Secret stores sensitive config such as passwords and tokens. Both can be exposed as environment variables or mounted as files. Secrets are base64-encoded by default, not equivalent to full encryption unless the cluster enables encryption at rest.

### Pod vs Container

A container is a running process from an image. A Pod is the smallest Kubernetes/OpenShift scheduling unit and can contain one or more containers that share networking and volumes.

### Service Types

`ClusterIP` exposes a Service only inside the cluster. `NodePort` exposes it on every node at a static port. `LoadBalancer` asks the infrastructure provider for an external load balancer. In OpenShift, HTTP apps are commonly exposed externally with a Route on top of a ClusterIP Service.

### Health Probes

Readiness probes decide whether a Pod should receive traffic. Liveness probes decide whether the container should be restarted. Startup probes are useful for slow-starting apps, but this lab uses readiness and liveness only.

### Scaling

Scaling changes the number of Pod replicas. The Service sends traffic only to ready Pods matching its selector.

### Rolling Updates

A rolling update gradually replaces old Pods with new Pods. Readiness probes protect traffic flow by ensuring new Pods are ready before they receive requests.

## Common Troubleshooting Guide

### Pod CrashLoopBackOff

Meaning:

```text
The container starts, crashes, and OpenShift keeps restarting it.
```

Commands:

```powershell
oc get pods
oc logs <pod-name> --previous
oc describe pod <pod-name>
```

Likely causes:

- Bad DB URL.
- Kafka host not reachable.
- Missing Secret value.
- App startup exception.

### ImagePullBackOff

Meaning:

```text
OpenShift cannot pull the image.
```

Commands:

```powershell
oc describe pod <pod-name>
oc get events --sort-by=.lastTimestamp
```

Likely causes:

- Image was not pushed.
- Wrong image name.
- Wrong project in registry path.
- Registry authentication issue.

### Route Not Accessible

Commands:

```powershell
oc get route
oc get service
oc get endpoints documentetl
oc get pods
```

Likely causes:

- Pod is not ready.
- Service selector does not match Pod labels.
- Route points to wrong Service or target port.

### ConfigMap Change Not Reflected

Reason:

```text
Environment variables are loaded when the container starts.
```

Fix:

```powershell
oc apply -k openshift
oc rollout restart deployment/documentetl
```

### Secret Issues

Commands:

```powershell
oc get secret documentetl-secrets
oc describe pod <pod-name>
```

Likely causes:

- Secret does not exist.
- Deployment references the wrong key.
- Secret file mount path does not match `SPRING_CLOUD_GCP_CREDENTIALS_LOCATION`.

## Two-Hour Lab Plan

1. 15 minutes: Review Dockerfile and build with Podman.
2. 15 minutes: Review Deployment, Pod, container, replicas, probes.
3. 15 minutes: Review ConfigMap and Secret mapping to Spring properties.
4. 15 minutes: Review Service and Route.
5. 20 minutes: Push image and apply manifests.
6. 15 minutes: Verify Pods, logs, Service, Route.
7. 15 minutes: Practice scaling, rollout restart, rollout history.
8. 10 minutes: Practice troubleshooting questions.
