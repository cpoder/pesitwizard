# Bugs à corriger

## Client UI

### 1. Erreurs 500 sans messages clairs
**Priorité**: Haute
**Composant**: pesitwizard-client-api / pesitwizard-client-ui

Les erreurs HTTP 500 sont affichées de manière brute sans message utilisateur clair. Par exemple, lors de la création d'un calendrier avec un nom déjà existant, le serveur retourne une 500 au lieu d'une 409 Conflict avec un message explicite.

**Comportement actuel**:
- Erreur 500 avec stack trace dans les logs
- Pas de popup d'erreur claire côté UI

**Comportement attendu**:
- Retourner des codes HTTP appropriés (409 pour conflit, 400 pour validation)
- Afficher une popup d'erreur claire avec le message traduit
- Logger l'erreur côté serveur mais ne pas exposer les détails techniques à l'utilisateur

**Exemple de log**:
```
org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException: Unique index or primary key violation:
"PUBLIC.UKRKIY8C9P7WD8QPDRR0EGO03VG_INDEX_1 ON PUBLIC.BUSINESS_CALENDARS(NAME NULLS FIRST)
VALUES ( /* 1 */ 'Production Calendar' )"
```

---

### 2. Bug de planification horaire
**Priorité**: Haute
**Composant**: pesitwizard-client-api (ScheduleService)

Lors de la création d'une planification quotidienne à 9h30, le transfert s'exécute 24h après l'heure courante au lieu de s'exécuter le lendemain à 9h30.

**Étapes de reproduction**:
1. Créer un favori
2. Planifier ce favori en mode "Daily" à 09:30
3. Observer que l'exécution est programmée pour maintenant + 24h, pas pour demain 09:30

**Comportement actuel**:
- Si l'heure actuelle est 23:00, le prochain transfert est planifié pour 23:00 le lendemain
- L'heure configurée (09:30) est ignorée

**Comportement attendu**:
- Si l'heure actuelle est 23:00 et l'heure configurée est 09:30:
  - Le prochain transfert devrait être planifié pour 09:30 le lendemain
- Si l'heure actuelle est 08:00 et l'heure configurée est 09:30:
  - Le prochain transfert devrait être planifié pour 09:30 le jour même

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

### 4. Salt de chiffrement non partagé entre les pods serveur
**Priorité**: Critique
**Composant**: pesitwizard-server (déploiement k8s)

Chaque pod du StatefulSet génère son propre salt de chiffrement AES lors du démarrage. Les partenaires créés sur un pod ne peuvent pas être authentifiés sur un autre pod car le mot de passe ne peut pas être déchiffré.

**Erreur**:
```
ERROR c.p.s.handler.TcpConnectionHandler - Unexpected error: Decryption failed
    at com.pesitwizard.server.handler.ConnectionValidator.validatePartner
```

**Cause**:
- Le fichier `./config/encryption.salt` est généré au démarrage s'il n'existe pas
- Chaque pod génère un salt différent
- Le mot de passe du partenaire est chiffré avec le salt du pod où il a été créé
- Les connexions sont load-balancées et peuvent arriver sur un autre pod

**Solutions possibles**:
1. Utiliser un PersistentVolumeClaim partagé pour le fichier salt
2. Stocker le salt dans un Secret Kubernetes et le monter sur tous les pods
3. Utiliser HashiCorp Vault au lieu du chiffrement AES local
4. Générer le salt de manière déterministe à partir d'une valeur commune

---

### 5. Barre de progression des transferts non fonctionnelle
**Priorité**: Haute
**Composant**: pesitwizard-client-ui / WebSocket

La barre de progression des transferts ne se met pas à jour pendant le transfert. Le statut passe directement de "IN_PROGRESS" à "COMPLETED" sans afficher la progression intermédiaire.

**Cause probable**:
- Problème de connexion WebSocket entre le client UI et l'API
- Les événements de progression ne sont pas émis ou reçus correctement

**À investiguer**:
- Vérifier la configuration WebSocket (STOMP/SockJS)
- Vérifier que les événements `TransferProgressEvent` sont bien publiés
- Vérifier la souscription côté UI aux topics WebSocket

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
