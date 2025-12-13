# Guide de déploiement

Ce guide explique comment déployer Vectis dans différents environnements.

## Prérequis

- **Kubernetes** 1.25+ (K3s, EKS, GKE, AKS, ou autre)
- **kubectl** configuré avec accès au cluster
- **Helm** 3.x (optionnel, recommandé)
- **PostgreSQL** 14+ (peut être externe ou déployé dans le cluster)

## Option 1 : Déploiement rapide avec Helm

### Installation

```bash
# Ajouter le repo Helm Vectis
helm repo add vectis https://charts.vectis.cloud
helm repo update

# Installer Vectis Server
helm install vectis-server vectis/vectis-server \
  --namespace vectis \
  --create-namespace \
  --set postgresql.enabled=true \
  --set replicas=3

# Vérifier le déploiement
kubectl get pods -n vectis
```

### Configuration

Créer un fichier `values.yaml` personnalisé :

```yaml
# values.yaml
replicas: 3

server:
  serverId: "MY_VECTIS_SERVER"
  port: 5000
  tls:
    enabled: true
    certSecret: vectis-tls-cert

postgresql:
  enabled: true
  # Ou utiliser une base externe :
  # enabled: false
  # external:
  #   host: my-postgres.example.com
  #   port: 5432
  #   database: vectis
  #   username: vectis
  #   password: secret

ingress:
  enabled: true
  className: nginx
  hosts:
    - host: vectis.example.com
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

Appliquer :

```bash
helm upgrade --install vectis-server vectis/vectis-server \
  --namespace vectis \
  -f values.yaml
```

## Option 2 : Déploiement manuel (kubectl)

### 1. Créer le namespace

```bash
kubectl create namespace vectis
```

### 2. Déployer PostgreSQL (si nécessaire)

```bash
kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
  namespace: vectis
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
        image: postgres:15-alpine
        env:
        - name: POSTGRES_DB
          value: vectis
        - name: POSTGRES_USER
          value: vectis
        - name: POSTGRES_PASSWORD
          value: vectis
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
  namespace: vectis
spec:
  selector:
    app: postgres
  ports:
  - port: 5432
    targetPort: 5432
EOF
```

### 3. Déployer Vectis Server

```bash
kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vectis-server
  namespace: vectis
spec:
  replicas: 3
  selector:
    matchLabels:
      app: vectis-server
  template:
    metadata:
      labels:
        app: vectis-server
    spec:
      containers:
      - name: vectis-server
        image: ghcr.io/cpoder/vectis/vectis-server:latest
        env:
        - name: VECTIS_SERVER_ID
          value: "MY_VECTIS_SERVER"
        - name: VECTIS_SERVER_PORT
          value: "5000"
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:postgresql://postgres:5432/vectis"
        - name: SPRING_DATASOURCE_USERNAME
          value: "vectis"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "vectis"
        ports:
        - containerPort: 5000
          name: vectis
        - containerPort: 8080
          name: http
        - containerPort: 7800
          name: jgroups
---
apiVersion: v1
kind: Service
metadata:
  name: vectis-server
  namespace: vectis
spec:
  type: LoadBalancer
  selector:
    app: vectis-server
  ports:
  - name: vectis
    port: 5000
    targetPort: 5000
EOF
```

## Déploiement sur Cloud Providers

### AWS EKS

```bash
# Créer le cluster EKS
eksctl create cluster \
  --name vectis-cluster \
  --region eu-west-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 3

# Configurer kubectl
aws eks update-kubeconfig --name vectis-cluster --region eu-west-1

# Installer Vectis
helm install vectis-server vectis/vectis-server \
  --namespace vectis \
  --create-namespace \
  --set postgresql.enabled=true
```

### Google GKE

```bash
# Créer le cluster GKE
gcloud container clusters create vectis-cluster \
  --zone europe-west1-b \
  --num-nodes 3 \
  --machine-type e2-medium

# Configurer kubectl
gcloud container clusters get-credentials vectis-cluster --zone europe-west1-b

# Installer Vectis
helm install vectis-server vectis/vectis-server \
  --namespace vectis \
  --create-namespace
```

### Azure AKS

```bash
# Créer le groupe de ressources
az group create --name vectis-rg --location westeurope

# Créer le cluster AKS
az aks create \
  --resource-group vectis-rg \
  --name vectis-cluster \
  --node-count 3 \
  --node-vm-size Standard_B2s \
  --generate-ssh-keys

# Configurer kubectl
az aks get-credentials --resource-group vectis-rg --name vectis-cluster

# Installer Vectis
helm install vectis-server vectis/vectis-server \
  --namespace vectis \
  --create-namespace
```

### K3s (On-Premise / Edge)

```bash
# Installer K3s sur le serveur principal
curl -sfL https://get.k3s.io | sh -

# Récupérer le kubeconfig
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

# Installer Vectis
helm install vectis-server vectis/vectis-server \
  --namespace vectis \
  --create-namespace \
  --set service.type=NodePort
```

## Configuration TLS

### Générer un certificat auto-signé (développement)

```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout vectis.key -out vectis.crt \
  -subj "/CN=vectis.example.com"

kubectl create secret tls vectis-tls \
  --cert=vectis.crt --key=vectis.key \
  -n vectis
```

### Utiliser Let's Encrypt (production)

Installer cert-manager :

```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml
```

Créer un ClusterIssuer :

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

Vectis expose des métriques Prometheus sur `/actuator/prometheus`.

```bash
# Installer kube-prometheus-stack
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/kube-prometheus-stack -n monitoring --create-namespace

# Configurer le ServiceMonitor pour Vectis
kubectl apply -f - <<EOF
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: vectis-server
  namespace: vectis
spec:
  selector:
    matchLabels:
      app: vectis-server
  endpoints:
  - port: http
    path: /actuator/prometheus
EOF
```

## Backup & Restore

### Backup PostgreSQL

```bash
# Backup
kubectl exec -n vectis deploy/postgres -- \
  pg_dump -U vectis vectis > backup.sql

# Restore
kubectl exec -i -n vectis deploy/postgres -- \
  psql -U vectis vectis < backup.sql
```

### Backup des certificats

```bash
kubectl get secret vectis-tls -n vectis -o yaml > vectis-tls-backup.yaml
```

## Mise à jour

```bash
# Mettre à jour le chart Helm
helm repo update
helm upgrade vectis-server vectis/vectis-server -n vectis

# Ou mettre à jour l'image manuellement
kubectl set image deployment/vectis-server \
  vectis-server=ghcr.io/cpoder/vectis/vectis-server:v1.2.0 \
  -n vectis
```

## Dépannage

### Vérifier les logs

```bash
kubectl logs -f deployment/vectis-server -n vectis
```

### Vérifier la connectivité

```bash
# Tester la connexion PeSIT
kubectl run test-client --rm -it --image=ghcr.io/cpoder/vectis/vectis-client:latest \
  -- java -jar vectis-client.jar --host vectis-server --port 5000 --test
```

### Problèmes courants

| Problème | Solution |
|----------|----------|
| Pods en CrashLoopBackOff | Vérifier les logs, souvent un problème de connexion DB |
| LoadBalancer Pending | Vérifier que le cloud provider supporte les LoadBalancer |
| Certificat invalide | Vérifier que le secret TLS existe et est valide |
| Leader election échoue | Vérifier que JGroups peut communiquer (port 7800) |
