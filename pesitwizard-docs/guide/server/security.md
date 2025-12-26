# Sécurité

## Vue d'ensemble

La sécurité du serveur PeSIT Wizard repose sur plusieurs niveaux :

1. **Authentification PeSIT** : Identifiants partenaire/mot de passe
2. **Chiffrement TLS** : PeSIT-E sur TLS 1.2/1.3
3. **Authentification API** : Basic Auth ou JWT
4. **Réseau** : Firewall, VPN, IP whitelisting

## Authentification PeSIT

### Configuration des partenaires

Chaque partenaire doit être enregistré avec un identifiant et mot de passe :

```bash
curl -X POST http://localhost:8080/api/partners \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "partnerId": "BANQUE_XYZ",
    "password": "MotDePasseComplexe123!",
    "enabled": true
  }'
```

### Politique de mots de passe

Recommandations :
- Minimum 12 caractères
- Mélange majuscules/minuscules/chiffres/symboles
- Rotation tous les 90 jours
- Pas de réutilisation des 5 derniers mots de passe

## Chiffrement TLS et mTLS

PeSIT Wizard supporte deux modes de sécurisation TLS :

| Mode | Description | Cas d'usage |
|------|-------------|-------------|
| **TLS simple** | Seul le serveur présente un certificat | Partenaires de confiance sur réseau sécurisé |
| **mTLS** | Client ET serveur présentent un certificat | Production, haute sécurité |

### Architecture avec CA privée

PeSIT Wizard intègre une **Autorité de Certification (CA) privée** pour simplifier la gestion des certificats :

```
                    ┌─────────────────────┐
                    │   CA Privée PeSIT   │
                    │   (pesit-ca)        │
                    └──────────┬──────────┘
                               │ signe
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
     ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
     │ Cert Serveur│   │ Cert Client │   │ Cert Client │
     │  PeSIT      │   │  Partner A  │   │  Partner B  │
     └─────────────┘   └─────────────┘   └─────────────┘
```

## Gestion des certificats via l'interface utilisateur

L'interface PeSIT Wizard Client permet de gérer l'ensemble de l'infrastructure de certificats de manière visuelle.

### Accéder à la gestion des certificats

Dans le menu latéral, cliquez sur **Certificates** pour accéder à l'interface de gestion :

![Navigation vers Certificates](/screenshots/certificates-nav.png)

### 1. Initialiser la CA privée

Lors du premier accès, la CA n'est pas initialisée. Cliquez sur le bouton **Initialize CA** :

![Initialisation de la CA](/screenshots/ca-init.png)

Une fois initialisée, vous verrez le statut de la CA avec son DN (Distinguished Name) :

![CA initialisée](/screenshots/ca-initialized.png)

### 2. Générer un certificat pour un partenaire

Cliquez sur **Generate Partner Cert** ou sur la carte "Generate Partner Certificate" :

![Générer un certificat partenaire](/screenshots/generate-partner-cert.png)

Remplissez le formulaire :
- **Partner ID** : Identifiant unique du partenaire (ex: `BANQUE_XYZ`)
- **Common Name** : FQDN ou nom du certificat (ex: `banque-xyz.example.com`)
- **Purpose** : `Client (mTLS)` pour l'authentification client
- **Validity** : Durée de validité en jours (défaut: 365)

![Formulaire de génération](/screenshots/generate-form.png)

### 3. Télécharger le certificat CA

Pour que les clients puissent faire confiance au serveur, ils doivent importer le certificat CA dans leur truststore.

Cliquez sur **Download CA Cert** :

![Télécharger le certificat CA](/screenshots/download-ca-cert.png)

Vous pouvez :
- **Copier** le certificat dans le presse-papiers
- **Télécharger** le fichier `pesit-ca.pem`

### 4. Signer un CSR (Certificate Signing Request)

Si un client préfère générer sa propre clé privée, il peut vous envoyer un CSR à signer.

Cliquez sur **Sign CSR** :

![Signer un CSR](/screenshots/sign-csr.png)

1. Collez le CSR (format PEM) fourni par le client
2. Sélectionnez le **Purpose** et la **Validity**
3. Optionnellement, associez un **Partner ID**
4. Cliquez sur **Sign CSR**

Le certificat signé sera affiché et téléchargeable :

![Certificat signé](/screenshots/signed-cert-result.png)

### 5. Visualiser les certificats

L'interface affiche tous les keystores et truststores configurés :

![Liste des certificats](/screenshots/certificates-list.png)

Pour chaque certificat, vous pouvez voir :
- Le nom et le type (Keystore/Truststore)
- Le DN du sujet
- La date d'expiration (en rouge si < 30 jours)
- Le partenaire associé

---

## Configuration mTLS via fichier (avancé)

Pour les déploiements automatisés, vous pouvez également utiliser l'API REST ou le fichier de configuration.

### Initialiser la CA via API

```bash
curl -X POST http://localhost:8080/api/v1/certificates/ca/initialize \
  -u admin:admin
```

### Configurer mTLS

```yaml
pesitwizard:
  ssl:
    enabled: true
    keystore-name: default-keystore       # Certificat serveur
    truststore-name: pesit-ca-truststore  # CA pour valider les clients
    client-auth: NEED                     # NONE, WANT, NEED
    protocol: TLSv1.3
    verify-certificate-chain: true
  
  ca:
    enabled: true
    ca-keystore-name: pesit-ca-keystore
    ca-truststore-name: pesit-ca-truststore
    ca-keystore-password: ${PESIT_CA_PASSWORD:changeit}
```

### Modes d'authentification client

| Mode | Comportement |
|------|--------------|
| `NONE` | TLS simple, pas de certificat client requis |
| `WANT` | Le serveur demande un certificat mais accepte les connexions sans |
| `NEED` | **mTLS obligatoire** - le client DOIT présenter un certificat valide |

---

## Guide Client : Obtenir un certificat signé

Les clients PeSIT doivent obtenir un certificat signé par la CA du serveur pour se connecter en mTLS.

### Option 1 : Certificat généré par l'administrateur (recommandé)

L'administrateur du serveur PeSIT Wizard génère un certificat pour le partenaire via l'interface :

1. **Administrateur** : Génère le certificat via l'UI (voir section "Générer un certificat pour un partenaire")
2. **Administrateur** : Exporte et transmet le keystore au client de manière sécurisée
3. **Client** : Importe le keystore reçu dans sa configuration

![Workflow certificat généré](/screenshots/workflow-generated-cert.png)

### Option 2 : CSR fourni par le client (plus sécurisé)

Si le client préfère générer sa propre clé privée (plus sécurisé) :

#### Côté client : Générer une clé et un CSR

```bash
# 1. Générer la clé privée (le client la garde secrète)
openssl genrsa -out client-key.pem 2048

# 2. Générer la demande de signature (CSR)
openssl req -new \
  -key client-key.pem \
  -out client.csr \
  -subj "/CN=banque-xyz.example.com/O=Banque XYZ/C=FR"

# 3. Afficher le CSR en base64 pour l'envoyer au serveur
cat client.csr
```

#### Côté serveur : Signer le CSR via l'UI

L'administrateur signe le CSR via l'interface **Certificates > Sign CSR** :

![Signer le CSR du client](/screenshots/sign-client-csr.png)

1. Collez le CSR reçu du client
2. Sélectionnez **Client (mTLS)** comme purpose
3. Cliquez sur **Sign CSR**
4. Téléchargez le certificat signé et envoyez-le au client

Alternativement, via API :

```bash
curl -X POST "http://localhost:8080/api/v1/certificates/ca/sign" \
  -u admin:admin \
  -d "csrPem=$(cat client.csr)" \
  -d "purpose=CLIENT" \
  -d "validityDays=365" \
  -d "partnerId=BANQUE_XYZ"
```

#### Côté client : Créer le keystore

```bash
# 1. Sauvegarder le certificat signé
echo "-----BEGIN CERTIFICATE-----
MIID....
-----END CERTIFICATE-----" > client-cert.pem

# 2. Télécharger le certificat CA (pour le truststore)
curl -o ca-cert.pem http://localhost:8080/api/v1/certificates/ca/certificate \
  -u admin:admin

# 3. Créer le keystore PKCS12 avec la clé et le certificat
openssl pkcs12 -export \
  -in client-cert.pem \
  -inkey client-key.pem \
  -out client-keystore.p12 \
  -name client \
  -password pass:changeit

# 4. Créer le truststore avec le certificat CA
keytool -importcert \
  -alias pesit-ca \
  -file ca-cert.pem \
  -keystore client-truststore.p12 \
  -storetype PKCS12 \
  -storepass changeit \
  -noprompt
```

---

## Guide Client : Importer le certificat serveur

Pour que le client fasse confiance au serveur PeSIT Wizard, il doit importer le certificat CA dans son truststore.

### Télécharger le certificat CA via l'UI

Le certificat CA peut être téléchargé via l'interface **Certificates > Download CA Cert** :

![Téléchargement du certificat CA](/screenshots/client-download-ca.png)

Le client peut également le télécharger via l'API publique :

```bash
curl -o pesit-ca.pem \
  http://localhost:8080/api/v1/certificates/ca/certificate \
  -u admin:admin
```

### Importer dans un truststore Java (PKCS12)

```bash
# Créer ou mettre à jour le truststore
keytool -importcert \
  -alias pesit-wizard-ca \
  -file pesit-ca.pem \
  -keystore truststore.p12 \
  -storetype PKCS12 \
  -storepass changeit \
  -noprompt

# Vérifier l'import
keytool -list -keystore truststore.p12 -storepass changeit
```

### Importer dans un truststore JKS (legacy)

```bash
keytool -importcert \
  -alias pesit-wizard-ca \
  -file pesit-ca.pem \
  -keystore truststore.jks \
  -storetype JKS \
  -storepass changeit \
  -noprompt
```

### Configuration client Java

```java
// Charger le keystore (certificat client)
KeyStore keyStore = KeyStore.getInstance("PKCS12");
keyStore.load(new FileInputStream("client-keystore.p12"), "changeit".toCharArray());

// Charger le truststore (certificat CA serveur)
KeyStore trustStore = KeyStore.getInstance("PKCS12");
trustStore.load(new FileInputStream("client-truststore.p12"), "changeit".toCharArray());

// Créer le SSLContext
KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
kmf.init(keyStore, "changeit".toCharArray());

TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
tmf.init(trustStore);

SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
```

---

## Vérifier un certificat

### Vérifier qu'un certificat est signé par la CA

```bash
curl -X POST "http://localhost:8080/api/v1/certificates/ca/verify" \
  -u admin:admin \
  -d "certificatePem=$(cat client-cert.pem)"
```

Réponse si valide :
```json
{
  "message": "Certificate is valid and signed by our CA"
}
```

### Vérifier un certificat avec OpenSSL

```bash
# Vérifier la chaîne de confiance
openssl verify -CAfile pesit-ca.pem client-cert.pem

# Afficher les détails du certificat
openssl x509 -in client-cert.pem -text -noout
```

---

## Résumé des endpoints CA

| Endpoint | Méthode | Description |
|----------|---------|-------------|
| `/api/v1/certificates/ca/initialize` | POST | Initialiser la CA privée |
| `/api/v1/certificates/ca/certificate` | GET | Télécharger le certificat CA (PEM) |
| `/api/v1/certificates/ca/csr` | POST | Générer un CSR |
| `/api/v1/certificates/ca/sign` | POST | Signer un CSR |
| `/api/v1/certificates/ca/partner/{id}/generate` | POST | Générer un certificat complet pour un partenaire |
| `/api/v1/certificates/ca/verify` | POST | Vérifier un certificat |

---

## Workflow complet mTLS

```
┌──────────────────────────────────────────────────────────────────┐
│                      SERVEUR PESIT WIZARD                        │
├──────────────────────────────────────────────────────────────────┤
│  1. POST /ca/initialize         → Créer la CA                    │
│  2. Configurer ssl.client-auth: NEED                            │
│  3. POST /ca/partner/XXX/generate  → Générer cert partenaire    │
│  4. GET /ca/certificate         → Distribuer cert CA            │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│                         CLIENT PESIT                             │
├──────────────────────────────────────────────────────────────────┤
│  1. Récupérer le keystore partenaire (clé + cert signé)         │
│     OU générer CSR et faire signer                               │
│  2. Importer cert CA dans truststore                            │
│  3. Configurer le client avec keystore + truststore             │
│  4. Connexion mTLS au serveur port 5000                         │
└──────────────────────────────────────────────────────────────────┘
```

## Sécurité de l'API REST

### Basic Authentication

```yaml
pesitwizard:
  admin:
    username: admin
    password: ${ADMIN_PASSWORD}  # Via variable d'environnement
```

### Changer le mot de passe admin

```bash
# Via variable d'environnement
docker run -e VECTIS_ADMIN_PASSWORD=NouveauMotDePasse ...
```

### HTTPS pour l'API

En production, placez un reverse proxy (nginx, traefik) devant l'API :

```nginx
server {
    listen 443 ssl;
    server_name pesitwizard-admin.monentreprise.com;
    
    ssl_certificate /etc/ssl/certs/server.crt;
    ssl_certificate_key /etc/ssl/private/server.key;
    
    location / {
        proxy_pass http://pesitwizard-server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## Sécurité réseau

### Firewall

Ports à ouvrir :

| Port | Service | Accès |
|------|---------|-------|
| 5000 | PeSIT | Partenaires uniquement |
| 8080 | API REST | Interne uniquement |

### IP Whitelisting

Restreindre l'accès PeSIT aux IPs connues :

```yaml
# NetworkPolicy Kubernetes
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: pesitwizard-server-policy
spec:
  podSelector:
    matchLabels:
      app: pesitwizard-server
  policyTypes:
  - Ingress
  ingress:
  - from:
    - ipBlock:
        cidr: 10.0.0.0/8  # Réseau interne
    - ipBlock:
        cidr: 203.0.113.0/24  # IPs de la banque
    ports:
    - port: 5000
```

### VPN

Pour les échanges sensibles, utilisez un VPN site-à-site :

```
[Votre réseau] ──VPN IPSec── [Réseau banque]
      │                            │
      ▼                            ▼
 PeSIT Wizard Client              PeSIT Wizard Server
```

## Audit et traçabilité

### Logs d'accès

Tous les accès sont journalisés :

```
2025-01-10 10:30:00 INFO  [CONNECT] Partner=BANQUE_XYZ IP=203.0.113.50 Status=SUCCESS
2025-01-10 10:30:01 INFO  [CREATE] Partner=BANQUE_XYZ File=VIREMENT.XML Status=SUCCESS
2025-01-10 10:30:05 INFO  [RELEASE] Partner=BANQUE_XYZ Duration=5s Bytes=15234
```

### Rétention des logs

```yaml
logging:
  file:
    name: /var/log/pesitwizard/pesitwizard-server.log
    max-size: 100MB
    max-history: 90  # 90 jours
```

### Export vers SIEM

Configurez Filebeat ou Fluentd pour envoyer les logs vers votre SIEM :

```yaml
# filebeat.yml
filebeat.inputs:
- type: log
  paths:
    - /var/log/pesitwizard/*.log
  
output.elasticsearch:
  hosts: ["elasticsearch:9200"]
  index: "pesitwizard-logs-%{+yyyy.MM.dd}"
```

## Checklist sécurité

- [ ] Mots de passe partenaires complexes
- [ ] TLS activé (PeSIT-E)
- [ ] Certificats valides et non expirés
- [ ] API protégée par HTTPS
- [ ] Mot de passe admin changé
- [ ] Firewall configuré
- [ ] IP whitelisting activé
- [ ] Logs centralisés
- [ ] Alertes configurées
- [ ] Sauvegardes testées
