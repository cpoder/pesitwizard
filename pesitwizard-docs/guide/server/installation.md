# PeSIT Wizard Server Installation

The PeSIT Wizard server allows receiving files from external partners. It is designed to be deployed on Kubernetes with high availability.

## Docker Deployment

For a simple deployment without Kubernetes:

```bash
docker run -d \
  --name pesitwizard-server \
  -p 6502:6502 \
  -p 5001:5001 \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=postgres \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/pesitwizard \
  -e SPRING_DATASOURCE_USERNAME=pesitwizard \
  -e SPRING_DATASOURCE_PASSWORD=pesitwizard \
  -e PESIT_CLUSTER_ENABLED=false \
  -v pesitwizard-data:/data \
  ghcr.io/pesitwizard/pesitwizard/pesitwizard-server:latest
```

## Docker Compose

For a single-node server with PostgreSQL:

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: pesitwizard
      POSTGRES_USER: pesitwizard
      POSTGRES_PASSWORD: pesitwizard
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U pesitwizard -d pesitwizard"]
      interval: 5s
      timeout: 5s
      retries: 5

  pesitwizard-server:
    image: ghcr.io/pesitwizard/pesitwizard/pesitwizard-server:latest
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=postgres
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/pesitwizard
      - SPRING_DATASOURCE_USERNAME=pesitwizard
      - SPRING_DATASOURCE_PASSWORD=pesitwizard
      - PESIT_CLUSTER_ENABLED=false
    ports:
      - "6502:6502"   # PeSIT protocol
      - "5001:5001"   # PeSIT TLS
      - "8080:8080"   # REST API
    volumes:
      - server_data:/data

volumes:
  postgres_data:
  server_data:
```

Start with:

```bash
docker compose up -d
```

For a two-node cluster setup, see the `docker-compose.yml` in the `pesitwizard-server` module directory.

## Kubernetes Deployment

### Create the Namespace

```bash
kubectl create namespace pesitwizard
```

### Deploy PostgreSQL

```yaml
# postgres.yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
  namespace: pesitwizard
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 10Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
  namespace: pesitwizard
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:16
        env:
        - name: POSTGRES_DB
          value: pesitwizard
        - name: POSTGRES_USER
          value: pesitwizard
        - name: POSTGRES_PASSWORD
          value: pesitwizard
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
      volumes:
      - name: data
        persistentVolumeClaim:
          claimName: postgres-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: pesitwizard
spec:
  ports:
  - port: 5432
  selector:
    app: postgres
```

### Deploy the PeSIT Wizard Server

```yaml
# pesitwizard-server.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pesitwizard-server
  namespace: pesitwizard
spec:
  replicas: 3
  selector:
    matchLabels:
      app: pesitwizard-server
  template:
    metadata:
      labels:
        app: pesitwizard-server
    spec:
      serviceAccountName: pesitwizard-server
      containers:
      - name: pesitwizard-server
        image: ghcr.io/pesitwizard/pesitwizard/pesitwizard-server:latest
        ports:
        - containerPort: 6502
          name: pesit
        - containerPort: 5001
          name: pesit-tls
        - containerPort: 8080
          name: http
        - containerPort: 7800
          name: jgroups
        env:
        - name: POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: POD_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
        - name: SPRING_PROFILES_ACTIVE
          value: postgres
        - name: SPRING_DATASOURCE_URL
          value: jdbc:postgresql://postgres:5432/pesitwizard
        - name: SPRING_DATASOURCE_USERNAME
          value: pesitwizard
        - name: SPRING_DATASOURCE_PASSWORD
          value: pesitwizard
        - name: PESIT_CLUSTER_ENABLED
          value: "true"
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
---
apiVersion: v1
kind: Service
metadata:
  name: pesitwizard-server
  namespace: pesitwizard
spec:
  type: LoadBalancer
  ports:
  - port: 6502
    name: pesit
  - port: 5001
    name: pesit-tls
  selector:
    app: pesitwizard-server
    pesitwizard-leader: "true"  # Route only to the leader
```

### Using the Helm Chart

#### Quick Install

```bash
curl -fsSL https://raw.githubusercontent.com/pesitwizard/pesitwizard/main/scripts/install-server.sh | bash
```

#### Manual Install

Install from the OCI registry:

```bash
helm install pesitwizard-server oci://ghcr.io/pesitwizard/charts/pesitwizard-server \
  --version 0.1.0 \
  --namespace pesitwizard \
  --create-namespace \
  --set database.host=postgres \
  --set database.port=5432 \
  --set database.name=pesitwizard \
  --set database.username=pesitwizard \
  --set database.password=pesitwizard
```

### RBAC for Labeling

```yaml
# rbac.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: pesitwizard-server
  namespace: pesitwizard
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: pesitwizard-server
  namespace: pesitwizard
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list", "patch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: pesitwizard-server
  namespace: pesitwizard
subjects:
- kind: ServiceAccount
  name: pesitwizard-server
roleRef:
  kind: Role
  name: pesitwizard-server
  apiGroup: rbac.authorization.k8s.io
```

### Apply

```bash
kubectl apply -f postgres.yaml
kubectl apply -f rbac.yaml
kubectl apply -f pesitwizard-server.yaml
```

## Verification

```bash
# Check the pods
kubectl get pods -n pesitwizard

# Check the leader
kubectl get pods -n pesitwizard -l pesitwizard-leader=true

# Check the service
kubectl get svc -n pesitwizard

# Leader logs
kubectl logs -n pesitwizard -l pesitwizard-leader=true
```

## Next Steps

- [Configuration](/guide/server/configuration)
- [Clustering](/guide/server/clustering)
- [Security](/guide/server/security)
