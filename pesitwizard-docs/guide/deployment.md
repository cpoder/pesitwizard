# Deployment Guide

This guide explains how to deploy PeSIT Wizard in different environments.

## Prerequisites

- **Kubernetes** 1.25+ (K3s, EKS, GKE, AKS, or other)
- **kubectl** configured with cluster access
- **Helm** 3.x (optional, recommended)
- **PostgreSQL** 14+ (can be external or deployed in the cluster)

## Option 1: Quick Deployment with Helm

### Installation

```bash
# Install PeSIT Wizard Server from local chart
helm install pesitwizard-server ./pesitwizard-helm-charts/pesitwizard-server \
  --namespace pesitwizard \
  --create-namespace \
  --set replicaCount=3 \
  --set database.host=postgres \
  --set database.name=pesitwizard \
  --set database.username=pesitwizard \
  --set database.password=pesitwizard

# Verify the deployment
kubectl get pods -n pesitwizard
```

### Configuration

Create a custom `values.yaml` file:

```yaml
# values.yaml
replicaCount: 3

config:
  pesit:
    serverId: "MY_PESIT_SERVER"

# External database configuration
database:
  host: my-postgres.example.com
  port: 5432
  name: pesitwizard
  username: pesitwizard
  password: secret

ingress:
  enabled: true
  className: nginx
  hosts:
    - host: pesitwizard.example.com
      paths:
        - path: /
          pathType: Prefix

resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

Apply:

```bash
helm upgrade --install pesitwizard-server ./pesitwizard-helm-charts/pesitwizard-server \
  --namespace pesitwizard \
  -f values.yaml
```

## Option 2: Manual Deployment (kubectl)

### 1. Create the Namespace

```bash
kubectl create namespace pesitwizard
```

### 2. Deploy PostgreSQL (if needed)

```bash
kubectl apply -f - <<EOF
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
        ports:
        - containerPort: 5432
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
  selector:
    app: postgres
  ports:
  - port: 5432
    targetPort: 5432
EOF
```

### 3. Deploy PeSIT Wizard Server

```bash
kubectl apply -f - <<EOF
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
      containers:
      - name: pesitwizard-server
        image: ghcr.io/pesitwizard/pesitwizard/pesitwizard-server:latest
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "postgres"
        - name: PESIT_SERVER_ID
          value: "MY_PESIT_SERVER"
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:postgresql://postgres:5432/pesitwizard"
        - name: SPRING_DATASOURCE_USERNAME
          value: "pesitwizard"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "pesitwizard"
        - name: PESIT_CLUSTER_ENABLED
          value: "true"
        ports:
        - containerPort: 6502
          name: pesit
        - containerPort: 5001
          name: pesit-tls
        - containerPort: 8080
          name: http
        - containerPort: 7800
          name: jgroups
---
apiVersion: v1
kind: Service
metadata:
  name: pesitwizard-server
  namespace: pesitwizard
spec:
  type: LoadBalancer
  selector:
    app: pesitwizard-server
  ports:
  - name: pesit
    port: 6502
    targetPort: 6502
  - name: pesit-tls
    port: 5001
    targetPort: 5001
EOF
```

## Deployment on Cloud Providers

### AWS EKS

```bash
# Create the EKS cluster
eksctl create cluster \
  --name pesitwizard-cluster \
  --region eu-west-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 3

# Configure kubectl
aws eks update-kubeconfig --name pesitwizard-cluster --region eu-west-1

# Install PeSIT Wizard
helm install pesitwizard-server ./pesitwizard-helm-charts/pesitwizard-server \
  --namespace pesitwizard \
  --create-namespace \
  --set database.host=postgres \
  --set database.name=pesitwizard \
  --set database.username=pesitwizard \
  --set database.password=pesitwizard
```

### Google GKE

```bash
# Create the GKE cluster
gcloud container clusters create pesitwizard-cluster \
  --zone europe-west1-b \
  --num-nodes 3 \
  --machine-type e2-medium

# Configure kubectl
gcloud container clusters get-credentials pesitwizard-cluster --zone europe-west1-b

# Install PeSIT Wizard
helm install pesitwizard-server ./pesitwizard-helm-charts/pesitwizard-server \
  --namespace pesitwizard \
  --create-namespace
```

### Azure AKS

```bash
# Create the resource group
az group create --name pesitwizard-rg --location westeurope

# Create the AKS cluster
az aks create \
  --resource-group pesitwizard-rg \
  --name pesitwizard-cluster \
  --node-count 3 \
  --node-vm-size Standard_B2s \
  --generate-ssh-keys

# Configure kubectl
az aks get-credentials --resource-group pesitwizard-rg --name pesitwizard-cluster

# Install PeSIT Wizard
helm install pesitwizard-server ./pesitwizard-helm-charts/pesitwizard-server \
  --namespace pesitwizard \
  --create-namespace
```

### K3s (On-Premise / Edge)

```bash
# Install K3s on the main server
curl -sfL https://get.k3s.io | sh -

# Get the kubeconfig
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

# Install PeSIT Wizard
helm install pesitwizard-server ./pesitwizard-helm-charts/pesitwizard-server \
  --namespace pesitwizard \
  --create-namespace \
  --set service.type=NodePort
```

## TLS Configuration

### Generate a Self-Signed Certificate (development)

```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout pesitwizard.key -out pesitwizard.crt \
  -subj "/CN=pesitwizard.example.com"

kubectl create secret tls pesitwizard-tls \
  --cert=pesitwizard.crt --key=pesitwizard.key \
  -n pesitwizard
```

### Use Let's Encrypt (production)

Install cert-manager:

```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml
```

Create a ClusterIssuer:

```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@example.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: nginx
```

## Monitoring

### Prometheus & Grafana

PeSIT Wizard exposes Prometheus metrics on `/actuator/prometheus`.

```bash
# Install kube-prometheus-stack
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/kube-prometheus-stack -n monitoring --create-namespace

# Configure the ServiceMonitor for PeSIT Wizard
kubectl apply -f - <<EOF
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: pesitwizard-server
  namespace: pesitwizard
spec:
  selector:
    matchLabels:
      app: pesitwizard-server
  endpoints:
  - port: http
    path: /actuator/prometheus
EOF
```

## Backup & Restore

### Backup PostgreSQL

```bash
# Backup
kubectl exec -n pesitwizard deploy/postgres -- \
  pg_dump -U pesitwizard pesitwizard > backup.sql

# Restore
kubectl exec -i -n pesitwizard deploy/postgres -- \
  psql -U pesitwizard pesitwizard < backup.sql
```

### Backup Certificates

```bash
kubectl get secret pesitwizard-tls -n pesitwizard -o yaml > pesitwizard-tls-backup.yaml
```

## Upgrades

```bash
# Update the Helm chart
helm upgrade pesitwizard-server ./pesitwizard-helm-charts/pesitwizard-server -n pesitwizard

# Or update the image manually
kubectl set image deployment/pesitwizard-server \
  pesitwizard-server=ghcr.io/pesitwizard/pesitwizard/pesitwizard-server:v1.2.0 \
  -n pesitwizard
```

## Troubleshooting

### Check the Logs

```bash
kubectl logs -f deployment/pesitwizard-server -n pesitwizard
```

### Verify Connectivity

```bash
# Test the PeSIT connection
kubectl run test-client --rm -it --image=ghcr.io/pesitwizard/pesitwizard/pesitwizard-client:latest \
  -- java -jar pesitwizard-client.jar --host pesitwizard-server --port 6502 --test
```

### Common Issues

| Issue | Solution |
|-------|----------|
| Pods in CrashLoopBackOff | Check logs, often a DB connection issue |
| LoadBalancer Pending | Verify cloud provider supports LoadBalancer |
| Invalid certificate | Verify that the TLS secret exists and is valid |
| Leader election fails | Verify that JGroups can communicate (TCP port 7800) |
