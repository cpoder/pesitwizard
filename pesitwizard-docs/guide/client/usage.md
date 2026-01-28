# Utilisation du Client

## Vidéo de démonstration

Cette vidéo présente le workflow complet de PeSIT Wizard Client : ajout d'un serveur, transfert de fichier, favoris, calendrier et planification.

<video controls width="100%" style="max-width: 800px; border-radius: 8px; margin: 1rem 0;">
  <source src="/videos/pesitwizard-demo.webm" type="video/webm">
  Votre navigateur ne supporte pas la balise video.
</video>

## Interface web

L'interface web permet de :
- Gérer les serveurs PeSIT cibles
- Envoyer et recevoir des fichiers
- Consulter l'historique des transferts
- Gérer les favoris et les planifications
- Tester les connexions

![Dashboard Client](/screenshots/client/dashboard.png)

### Envoyer un fichier (SEND)

1. Allez dans **Transfer**
2. Sélectionnez **SEND** comme direction
3. Sélectionnez le serveur cible
4. Renseignez :
   - **Partner ID** : Votre identifiant client
   - **Local File Path** : Chemin complet du fichier à envoyer
   - **Virtual File ID** : Identifiant du fichier virtuel (fourni par la banque)
5. Cliquez sur **Start Transfer**

![Transfer Send](/screenshots/client/transfer-send.png)

### Recevoir un fichier (RECEIVE)

1. Allez dans **Transfer**
2. Sélectionnez **RECEIVE** comme direction
3. Sélectionnez le serveur source
4. Renseignez :
   - **Partner ID** : Votre identifiant client
   - **Save To Path** : Chemin de destination (supporte les placeholders)
   - **Virtual File ID** : Identifiant du fichier virtuel
5. Cliquez sur **Start Transfer**

![Transfer Receive](/screenshots/client/transfer-receive.png)

#### Placeholders pour les chemins

Pour les transferts RECEIVE, vous pouvez utiliser des placeholders dynamiques dans le chemin de destination :

| Placeholder | Description |
|-------------|-------------|
| `${partner}` | ID du partenaire |
| `${virtualFile}` | Nom du fichier virtuel (PI 12) |
| `${server}` | ID du serveur |
| `${serverName}` | Nom du serveur |
| `${timestamp}` | Horodatage (yyyyMMdd_HHmmss) |
| `${date}` | Date (yyyyMMdd) |
| `${time}` | Heure (HHmmss) |
| `${year}`, `${month}`, `${day}` | Composants de date |
| `${uuid}` | UUID unique |

**Exemple** : `/data/received/${partner}/${virtualFile}_${timestamp}.dat`

Résultat : `/data/received/PARTNER01/DATA_FILE_20251211_213000.dat`

::: tip Note PeSIT
Le protocole PeSIT ne transmet pas le nom du fichier physique, uniquement l'identifiant du fichier virtuel (PI 12). Les placeholders `${file}`, `${basename}`, `${ext}` ne sont donc pas disponibles.
:::

## API REST

### Envoyer un fichier

```bash
curl -X POST http://localhost:9081/api/transfers/send \
  -H "Content-Type: multipart/form-data" \
  -F "file=@virement.xml" \
  -F "serverId=1" \
  -F "remoteFilename=VIREMENT_20250110.XML" \
  -F "partnerId=MON_ENTREPRISE" \
  -F "virtualFile=VIREMENTS"
```

**Réponse** :
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "direction": "SEND",
  "filename": "VIREMENT_20250110.XML",
  "size": 15234,
  "startTime": "2025-01-10T10:30:00Z",
  "endTime": "2025-01-10T10:30:05Z"
}
```

### Recevoir un fichier

```bash
curl -X POST http://localhost:9081/api/transfers/receive \
  -H "Content-Type: application/json" \
  -d '{
    "serverId": 1,
    "remoteFilename": "RELEVE_20250110.XML",
    "partnerId": "MON_ENTREPRISE",
    "virtualFile": "RELEVES"
  }'
```

**Réponse** :
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "status": "COMPLETED",
  "direction": "RECEIVE",
  "filename": "RELEVE_20250110.XML",
  "localPath": "/data/received/RELEVE_20250110.XML",
  "size": 8542
}
```

### Télécharger un fichier reçu

```bash
curl -O http://localhost:9081/api/transfers/550e8400-e29b-41d4-a716-446655440001/download
```

### Historique des transferts

```bash
curl http://localhost:9081/api/transfers
```

**Réponse** :
```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "status": "COMPLETED",
      "direction": "SEND",
      "filename": "VIREMENT_20250110.XML",
      "serverName": "BNP Paribas",
      "startTime": "2025-01-10T10:30:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

### Filtrer l'historique

```bash
# Par statut
curl "http://localhost:9081/api/transfers?status=FAILED"

# Par direction
curl "http://localhost:9081/api/transfers?direction=SEND"

# Par date
curl "http://localhost:9081/api/transfers?from=2025-01-01&to=2025-01-31"

# Par serveur
curl "http://localhost:9081/api/transfers?serverId=1"
```

## Favoris

Les favoris permettent de sauvegarder des configurations de transfert pour les réutiliser facilement.

![Favorites View](/screenshots/client/favorites.png)

### Créer un favori

1. Effectuez un transfert depuis la page **Transfer** ou **History**
2. Cliquez sur l'étoile pour ajouter aux favoris
3. Donnez un nom au favori

### Éditer un favori

1. Allez dans **Favorites**
2. Cliquez sur l'icône crayon ✏️
3. Modifiez les paramètres :
   - Nom et description
   - Serveur cible
   - Partner ID
   - Virtual File
   - Chemin local (avec placeholders pour RECEIVE)
4. Cliquez sur **Save Changes**

![Edit Favorite](/screenshots/client/favorite-edit.png)

::: info Synchronisation des planifications
Lorsque vous modifiez un favori, toutes les planifications liées sont automatiquement mises à jour.
:::

### Exécuter un favori

Cliquez sur le bouton **Execute** pour lancer immédiatement le transfert.

## Planifications

Les planifications permettent d'automatiser les transferts à des moments précis.

![Schedules View](/screenshots/client/schedules.png)

### Créer une planification

1. Depuis la page **Favorites**, cliquez sur l'icône calendrier 📅
2. Choisissez le type de planification :
   - **Daily** : Tous les jours à une heure précise
   - **Weekly** : Chaque semaine à un jour et heure précis
   - **Monthly** : Chaque mois à un jour précis
   - **Hourly** : Toutes les heures
   - **Interval** : Toutes les N minutes
   - **Once** : Une seule fois à une date/heure précise
   - **Cron** : Expression cron personnalisée
3. Configurez les options :
   - **Working days only** : Ignorer les weekends et jours fériés
   - **Business Calendar** : Utiliser un calendrier personnalisé
4. Cliquez sur **Create Schedule**

![Create Schedule](/screenshots/client/schedule-create.png)

### Calendriers métier

Les calendriers métier permettent de définir les jours ouvrés et fériés pour les planifications.

![Calendars View](/screenshots/client/calendars.png)

#### Créer un calendrier

1. Allez dans **Calendars**
2. Cliquez sur **New Calendar**
3. Configurez :
   - **Name** : Nom du calendrier (ex: "France")
   - **Timezone** : Fuseau horaire
   - **Working Days** : Jours ouvrés (cliquez pour activer/désactiver)
   - **Holidays** : Ajoutez les jours fériés
4. Cliquez sur **Create Calendar**

![Calendar Form](/screenshots/client/calendar-form.png)

#### Utiliser un calendrier

Lors de la création d'une planification, sélectionnez le calendrier dans le champ **Business Calendar**. Les transferts seront automatiquement reportés au prochain jour ouvré si la date prévue tombe un jour férié ou un weekend.

## Automatisation via scripts

### Script bash

```bash
#!/bin/bash
# send-virements.sh

API_URL="http://localhost:9081"
SERVER_ID=1
PARTNER_ID="MON_ENTREPRISE"
VIRTUAL_FILE="VIREMENTS"

for file in /data/outbox/*.xml; do
  filename=$(basename "$file")
  echo "Envoi de $filename..."
  
  response=$(curl -s -X POST "$API_URL/api/transfers/send" \
    -F "file=@$file" \
    -F "serverId=$SERVER_ID" \
    -F "remoteFilename=$filename" \
    -F "partnerId=$PARTNER_ID" \
    -F "virtualFile=$VIRTUAL_FILE")
  
  status=$(echo "$response" | jq -r '.status')
  
  if [ "$status" = "COMPLETED" ]; then
    echo "✓ $filename envoyé avec succès"
    mv "$file" /data/sent/
  else
    echo "✗ Échec de l'envoi de $filename"
    echo "$response"
  fi
done
```

### Cron job

```bash
# Récupérer les relevés tous les jours à 7h
0 7 * * * /opt/scripts/receive-releves.sh >> /var/log/pesitwizard.log 2>&1

# Envoyer les virements toutes les heures
0 * * * * /opt/scripts/send-virements.sh >> /var/log/pesitwizard.log 2>&1
```

## Codes d'erreur

| Code | Description | Action |
|------|-------------|--------|
| `CONNECTION_REFUSED` | Serveur injoignable | Vérifier host/port |
| `AUTH_FAILED` | Authentification échouée | Vérifier clientId/password |
| `PARTNER_UNKNOWN` | Partenaire non reconnu | Vérifier partnerId |
| `FILE_NOT_FOUND` | Fichier inexistant | Vérifier virtualFile/filename |
| `TIMEOUT` | Délai dépassé | Réessayer ou augmenter timeout |
