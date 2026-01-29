# Bugs à corriger

## Client UI

### 1. ~~Erreurs 500 sans messages clairs~~ ✅ CORRIGÉ
**Priorité**: Haute
**Composant**: pesitwizard-client-api / pesitwizard-client-ui
**Statut**: ✅ Corrigé (API côté) - UI à compléter pour afficher les messages

**Solution implémentée**:
- Ajout de `GlobalExceptionHandler` avec `@RestControllerAdvice`
- Retourne maintenant 409 Conflict pour les violations de contrainte unique
- Retourne 400 Bad Request pour les erreurs de validation
- Les stack traces ne sont plus exposées aux utilisateurs
- Configuration `server.error.include-stacktrace: never` ajoutée

**Fichiers créés/modifiés**:
- `pesitwizard-client/src/main/java/com/pesitwizard/client/exception/GlobalExceptionHandler.java`
- `pesitwizard-client/src/main/java/com/pesitwizard/client/exception/ApiError.java`
- `pesitwizard-client/src/main/resources/application.yml`

**Format de réponse d'erreur**:
```json
{
  "timestamp": "2026-01-29T20:00:00Z",
  "status": 409,
  "error": "CONFLICT",
  "message": "A resource with name 'Production Calendar' already exists",
  "path": "/api/v1/calendars"
}
```

**Note**: L'UI doit encore être mise à jour pour afficher ces messages dans des popups.

---

### 2. ~~Bug de planification horaire~~ ✅ CORRIGÉ
**Priorité**: Haute
**Composant**: pesitwizard-client-api (ScheduleService)
**Statut**: ✅ Corrigé

Lors de la création d'une planification quotidienne à 9h30, le transfert s'exécute 24h après l'heure courante au lieu de s'exécuter le lendemain à 9h30.

**Solution implémentée**:
- Ajout de la méthode `calculateInitialNextRunTime()` dans `TransferSchedulerService`
- Pour les schedules DAILY, WEEKLY, MONTHLY: utilise maintenant le `dailyTime` configuré
- Si l'heure du jour est déjà passée aujourd'hui, planifie pour le lendemain
- Même logique appliquée pour WEEKLY (prochain jour de la semaine) et MONTHLY (prochain mois)

**Fichier modifié**:
- `pesitwizard-client/src/main/java/com/pesitwizard/client/service/TransferSchedulerService.java`

---

## Client API (RECEIVE)

### 3. EOFException lors des transferts RECEIVE
**Priorité**: Moyenne
**Composant**: pesitwizard-client-api (PesitReceiveService)

Les transferts en mode RECEIVE échouent avec une EOFException alors que le serveur a correctement envoyé les données.

**Logs serveur** (succès):
```
READ: starting data transmission for /data/send/DEMOFILE
Sent ACK(READ)
Article 1: DTF 49 bytes
READ: sent 49 bytes in 1 entities, 0 sync points
Sent DTF.END
Client disconnected
```

**Logs client** (échec):
```
ERROR Receive xxx FAILED: null
java.io.EOFException: null
    at com.pesitwizard.client.pesit.PesitReceiveService.receiveData(PesitReceiveService.java:226)
```

**Hypothèse**: Le serveur ferme la connexion après DTF.END avant que le client n'ait fini de traiter la séquence de fin de transfert.

---

---

### 4. ~~Salt de chiffrement non partagé entre les pods serveur~~ ✅ CORRIGÉ
**Priorité**: Critique
**Composant**: pesitwizard-server (déploiement k8s)
**Statut**: ✅ Corrigé

Chaque pod du StatefulSet génère son propre salt de chiffrement AES lors du démarrage. Les partenaires créés sur un pod ne peuvent pas être authentifiés sur un autre pod car le mot de passe ne peut pas être déchiffré.

**Solution implémentée**:
- Ajout du support de la variable d'environnement `PESITWIZARD_SECURITY_ENCRYPTION_SALT`
- Le salt peut maintenant être partagé via un Secret Kubernetes (base64-encoded, 32 bytes)
- Configuration Helm chart: `config.security.encryptionSalt`
- Génère avec: `openssl rand -base64 32`

**Fichiers modifiés**:
- `pesitwizard-security/src/main/java/com/pesitwizard/security/AesSecretsProvider.java`
- `pesitwizard-security/src/main/java/com/pesitwizard/security/SecretsConfig.java`
- `pesitwizard-helm-charts/pesitwizard-server/values.yaml`
- `pesitwizard-helm-charts/pesitwizard-server/templates/secrets.yaml`
- `pesitwizard-helm-charts/pesitwizard-server/templates/deployment.yaml`

---

### 5. ~~Barre de progression des transferts non fonctionnelle~~ ✅ CORRIGÉ
**Priorité**: Haute
**Composant**: pesitwizard-client-ui / WebSocket
**Statut**: ✅ Corrigé

**Cause identifiée**:
- Incompatibilité entre la structure du `TransferEvent` backend et l'interface `TransferProgress` du frontend
- Backend envoyait: `type`, `totalBytes`, `percentComplete`
- Frontend attendait: `status`, `fileSize`, `percentage`

**Solution implémentée**:
- Ajout d'une interface `TransferEventPayload` pour représenter l'événement backend
- Ajout d'une fonction `normalizeEvent()` pour convertir les champs du backend au format frontend
- Mapping de `type` (enum) vers `status` (string)
- Mapping de `totalBytes` vers `fileSize`, `percentComplete` vers `percentage`
- Ajout du formatage des bytes en KB/MB/GB

**Fichier modifié**:
- `pesitwizard-client-ui/src/composables/useTransferProgress.ts`

---

### 6. EOFException lors des transferts SEND de gros fichiers
**Priorité**: Haute
**Composant**: pesitwizard-client-api / pesitwizard-server

Les transferts SEND de fichiers volumineux (>50MB) échouent avec une EOFException.

**Logs client**:
```
ERROR Transfer xxx FAILED: null
java.io.EOFException: null
```

**Hypothèses**:
- Problème de négociation maxEntitySize (PI_25)
- Timeout de connexion côté serveur
- Buffer de lecture/écriture insuffisant

---

## Notes

Ces bugs ont été identifiés lors de la création de la vidéo de démonstration (janvier 2026).
