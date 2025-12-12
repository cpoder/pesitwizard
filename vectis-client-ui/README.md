# PeSIT Client UI

Interface web pour effectuer des transferts de fichiers via le protocole PeSIT.

## Fonctionnalités

- **Envoi de fichiers** : Envoyer des fichiers vers un serveur PeSIT
- **Réception de fichiers** : Récupérer des fichiers depuis un serveur PeSIT
- **Gestion des serveurs** : Configurer plusieurs serveurs PeSIT cibles
- **Historique** : Consulter l'historique des transferts effectués
- **Test de connexion** : Vérifier la connectivité avec un serveur

## Prérequis

- Node.js 18+
- npm, yarn, pnpm ou bun
- Backend `pesit-client` en cours d'exécution (port 9081)

## Installation

```bash
npm install
```

## Développement

```bash
npm run dev
```

L'application sera accessible sur http://localhost:3001

## Build production

```bash
npm run build
```

## Configuration

L'URL du backend est configurée via la variable d'environnement `VITE_API_URL` ou dans le code source. Par défaut : `http://localhost:9081`.

## Stack technique

- Vue 3 + TypeScript
- Vuetify 3
- Vite
- Pinia (state management)
- Axios
