# Guide de déploiement

Ce guide explique comment déployer PeSIT Wizard dans différents environnements.

## Prérequis

- **Kubernetes** 1.25+ (K3s, EKS, GKE, AKS, ou autre)
- **kubectl** configuré avec accès au cluster
- **Helm** 3.x (optionnel, recommandé)
- **PostgreSQL** 14+ (peut être externe ou déployé dans le cluster)

## Option 1 : Déploiement rapide avec Helm

### Installation

```bash
# Ajouter le repo Helm PeSIT Wizard
helm repo add pesitwizard https://charts.pesitwizard.com
helm repo update

# Installer PeSIT Wizard Server
helm install pesitwizard-server pesitwizard/pesitwizard-server \
  --namespace pesitwizard \
  --create-namespace \
  --set postgresql.enabled=true \
  --set replicas=3

# Vérifier le déploiement
kubectl get pods -n pesitwizard
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
    certSecret: pesitwizard-tls-cert

postgresql:
  enabled: true
  # Ou utiliser une base externe :
  # enabled: false
  # external:
  #   host: my-postgres.example.com
  #   port: 5432
  #   database: pesitwizard
  #   username: pesitwizard
  #   password: secret

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

Appliquer :

```bash
helm upgrade --install pesitwizard-server pesitwizard/pesitwizard-server \
  --namespace pesitwizard \
  -f values.yaml
```

## Option 2 : Déploiement manuel (kubectl)

### 1. Créer le namespace

```bash
kubectl create namespace pesitwizard
```

### 2. Déployer PostgreSQL (si nécessaire)

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
        image: postgres:15-alpine
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

### 3. Déployer PeSIT Wizard Server

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
        - name: VECTIS_SERVER_ID
          value: "MY_VECTIS_SERVER"
        - name: VECTIS_SERVER_PORT
          value: "5000"
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:postgresql://postgres:5432/pesitwizard"
        - name: SPRING_DATASOURCE_USERNAME
          value: "pesitwizard"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "pesitwizard"
        ports:
        - containerPort: 5000
          name: pesitwizard
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
  - name: pesitwizard
    port: 5000
    targetPort: 5000
EOF
```

## Déploiement sur Cloud Providers

### AWS EKS

```bash
# Créer le cluster EKS
eksctl create cluster \
  --name pesitwizard-cluster \
  --region eu-west-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 3

# Configurer kubectl
aws eks update-kubeconfig --name pesitwizard-cluster --region eu-west-1

# Installer PeSIT Wizard
helm install pesitwizard-server pesitwizard/pesitwizard-server \
  --namespace pesitwizard \
  --create-namespace \
  --set postgresql.enabled=true
```

### Google GKE

```bash
# Créer le cluster GKE
gcloud container clusters create pesitwizard-cluster \
  --zone europe-west1-b \
  --num-nodes 3 \
  --machine-type e2-medium

# Configurer kubectl
gcloud container clusters get-credentials pesitwizard-cluster --zone europe-west1-b

# Installer PeSIT Wizard
helm install pesitwizard-server pesitwizard/pesitwizard-server \
  --namespace pesitwizard \
  --create-namespace
```

### Azure AKS

```bash
# Créer le groupe de ressources
az group create --name pesitwizard-rg --location westeurope

# Créer le cluster AKS
az aks create \
  --resource-group pesitwizard-rg \
  --name pesitwizard-cluster \
  --node-count 3 \
  --node-vm-size Standard_B2s \
  --generate-ssh-keys

# Configurer kubectl
az aks get-credentials --resource-group pesitwizard-rg --name pesitwizard-cluster

# Installer PeSIT Wizard
helm install pesitwizard-server pesitwizard/pesitwizard-server \
  --namespace pesitwizard \
  --create-namespace
```

### K3s (On-Premise / Edge)

```bash
# Installer K3s sur le serveur principal
curl -sfL https://get.k3s.io | sh -

# Récupérer le kubeconfig
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

# Installer PeSIT Wizard
helm install pesitwizard-server pesitwizard/pesitwizard-server \
  --namespace pesitwizard \
  --create-namespace \
  --set service.type=NodePort
```

## Configuration TLS

### Générer un certificat auto-signé (développement)

```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout pesitwizard.key -out pesitwizard.crt \
  -subj "/CN=pesitwizard.example.com"

kubectl create secret tls pesitwizard-tls \
  --cert=pesitwizard.crt --key=pesitwizard.key \
  -n pesitwizard
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

PeSIT Wizard expose des métriques Prometheus sur `/actuator/prometheus`.

```bash
# Installer kube-prometheus-stack
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install prometheus prometheus-community/kube-prometheus-stack -n monitoring --create-namespace

# Configurer le ServiceMonitor pour PeSIT Wizard
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

### Backup des certificats

```bash
kubectl get secret pesitwizard-tls -n pesitwizard -o yaml > pesitwizard-tls-backup.yaml
```

## Mise à jour

```bash
# Mettre à jour le chart Helm
helm repo update
helm upgrade pesitwizard-server pesitwizard/pesitwizard-server -n pesitwizard

# Ou mettre à jour l'image manuellement
kubectl set image deployment/pesitwizard-server \
  pesitwizard-server=ghcr.io/pesitwizard/pesitwizard/pesitwizard-server:v1.2.0 \
  -n pesitwizard
```

## Dépannage

### Vérifier les logs

```bash
kubectl logs -f deployment/pesitwizard-server -n pesitwizard
```

### Vérifier la connectivité

```bash
# Tester la connexion PeSIT
kubectl run test-client --rm -it --image=ghcr.io/pesitwizard/pesitwizard/pesitwizard-client:latest \
  -- java -jar pesitwizard-client.jar --host pesitwizard-server --port 5000 --test
```

### Problèmes courants

| Problème | Solution |
|----------|----------|
| Pods en CrashLoopBackOff | Vérifier les logs, souvent un problème de connexion DB |
| LoadBalancer Pending | Vérifier que le cloud provider supporte les LoadBalancer |
| Certificat invalide | Vérifier que le secret TLS existe et est valide |
| Leader election échoue | Vérifier que JGroups peut communiquer (port 7800) |
