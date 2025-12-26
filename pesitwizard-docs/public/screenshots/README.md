# Screenshots pour la documentation

Ce dossier contient les captures d'écran utilisées dans la documentation.

## Structure

```
screenshots/
├── client/           # Screenshots du client PeSIT Wizard
│   ├── dashboard.png
│   ├── transfer-send.png
│   ├── transfer-receive.png
│   ├── path-placeholders.png
│   ├── favorites.png
│   ├── favorite-edit.png
│   ├── schedules.png
│   ├── schedule-create.png
│   └── calendars.png
│
└── admin/            # Screenshots de l'admin PeSIT Wizard
    ├── dashboard.png
    ├── clusters.png
    ├── virtual-files.png
    ├── virtual-file-placeholders.png
    ├── partners.png
    └── transfers.png
```

## Comment générer les screenshots

### Prérequis

1. Démarrer les services :
   ```bash
   cd /home/cpo/pesitwizard/scripts
   ./start-all.sh
   ```

2. Accéder aux interfaces :
   - Client UI : http://localhost:5173
   - Admin UI : http://localhost:3000

### Screenshots à capturer

#### Client UI (http://localhost:5173)

| Fichier | Page | Description |
|---------|------|-------------|
| `dashboard.png` | Dashboard | Vue d'ensemble avec statistiques |
| `transfer-send.png` | Transfer | Formulaire SEND avec champs remplis |
| `transfer-receive.png` | Transfer | Formulaire RECEIVE avec placeholders |
| `path-placeholders.png` | Transfer | Composant placeholders avec tags visibles |
| `favorites.png` | Favorites | Liste des favoris avec cartes |
| `favorite-edit.png` | Favorites | Modal d'édition d'un favori |
| `schedules.png` | Schedules | Liste des planifications |
| `schedule-create.png` | Favorites | Modal de création de planification |
| `calendars.png` | Calendars | Liste des calendriers métier |
| `certificates-nav.png` | Navigation | Menu latéral avec Certificates surligné |
| `ca-init.png` | Certificates | CA non initialisée avec bouton Initialize |
| `ca-initialized.png` | Certificates | CA initialisée avec statut vert |
| `generate-partner-cert.png` | Certificates | Cards d'actions CA |
| `generate-form.png` | Certificates | Modal de génération certificat partenaire |
| `download-ca-cert.png` | Certificates | Modal téléchargement certificat CA |
| `sign-csr.png` | Certificates | Modal signature CSR vide |
| `signed-cert-result.png` | Certificates | Modal avec certificat signé |
| `certificates-list.png` | Certificates | Liste keystores/truststores |
| `client-download-ca.png` | Certificates | Téléchargement CA pour client |
| `sign-client-csr.png` | Certificates | Signature du CSR client |
| `workflow-generated-cert.png` | Certificates | Diagramme workflow Option 1 |

#### Admin UI (http://localhost:3000)

| Fichier | Page | Description |
|---------|------|-------------|
| `dashboard.png` | Dashboard | Vue d'ensemble des clusters |
| `clusters.png` | Clusters | Liste des clusters |
| `virtual-files.png` | Virtual Files | Liste des fichiers virtuels |
| `virtual-file-placeholders.png` | Virtual Files | Formulaire avec placeholders |
| `partners.png` | Partners | Liste des partenaires |
| `transfers.png` | Transfers | Historique des transferts |

### Conseils pour les captures

1. **Résolution** : 1280x800 minimum
2. **Format** : PNG
3. **Contenu** : Utiliser des données réalistes (ex: "BNP Paribas", "VIREMENTS_SEPA")
4. **Thème** : Mode clair
5. **Recadrage** : Éviter les barres de navigateur

### Outils recommandés

- **macOS** : Cmd+Shift+4 puis Espace pour capturer une fenêtre
- **Linux** : Flameshot, GNOME Screenshot
- **Windows** : Snipping Tool, ShareX
- **Navigateur** : Extension "Full Page Screen Capture"
