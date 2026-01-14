# Installation du Serveur PeSIT Wizard

Le serveur PeSIT Wizard permet de recevoir des fichiers de partenaires externes. Il est conçu pour être déployé sur Kubernetes avec haute disponibilité.

## Déploiement Docker

Pour un déploiement simple sans Kubernetes :

```bash
docker run -d \
  --name pesitwizard-server \
  -p 5000:5000 \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/pesitwizard \
  -e SPRING_DATASOURCE_USERNAME=pesitwizard \
  -e SPRING_DATASOURCE_PASSWORD=pesitwizard \
  -e VECTIS_CLUSTER_ENABLED=false \
  -v pesitwizard-data:/data \
  ghcr.io/pesitwizard/pesitwizard-server:latest
```

## Déploiement Kubernetes

### Créer le namespace

```bash
kubectl create namespace pesitwizard
```

### Déployer PostgreSQL

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

### Déployer le serveur PeSIT Wizard

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
        image: ghcr.io/pesitwizard/pesitwizard-server:latest
        ports:
        - containerPort: 5000
          name: pesitwizard
        - containerPort: 8080
          name: http
        env:
        - name: POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: POD_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
        - name: SPRING_DATASOURCE_URL
          value: jdbc:postgresql://postgres:5432/pesitwizard
        - name: VECTIS_CLUSTER_ENABLED
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
  - port: 5000
    name: pesitwizard
  selector:
    app: pesitwizard-server
    pesitwizard-leader: "true"  # Route uniquement vers le leader
```

### RBAC pour le labeling

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

### Appliquer

```bash
kubectl apply -f postgres.yaml
kubectl apply -f rbac.yaml
kubectl apply -f pesitwizard-server.yaml
```

## Vérification

```bash
# Vérifier les pods
kubectl get pods -n pesitwizard

# Vérifier le leader
kubectl get pods -n pesitwizard -l pesitwizard-leader=true

# Vérifier le service
kubectl get svc -n pesitwizard

# Logs du leader
kubectl logs -n pesitwizard -l pesitwizard-leader=true
```

## Prochaines étapes

- [Configuration](/guide/server/configuration)
- [Clustering](/guide/server/clustering)
- [Sécurité](/guide/server/security)
