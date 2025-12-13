# Vectis Documentation

Site de documentation pour la solution Vectis Cloud, construit avec VitePress.

## Développement

```bash
# Installer les dépendances
npm install

# Lancer le serveur de développement
npm run dev
```

Le site sera accessible sur http://localhost:5173

## Build

```bash
npm run build
```

Les fichiers statiques seront générés dans `.vitepress/dist/`.

## Structure

```
vectis-docs/
├── .vitepress/
│   └── config.ts          # Configuration VitePress
├── public/
│   └── api/               # Fichiers OpenAPI (OAS)
│       ├── openapi-client.yaml
│       ├── openapi-admin.yaml
│       └── openapi-server.yaml
├── guide/
│   ├── index.md           # Introduction
│   ├── quickstart.md      # Démarrage rapide
│   ├── architecture.md    # Architecture
│   ├── client/            # Documentation client
│   ├── server/            # Documentation serveur
│   └── admin/             # Documentation admin
├── api/
│   ├── index.md           # Vue d'ensemble API
│   ├── authentication.md  # Authentification
│   ├── client.md          # Client API
│   ├── admin.md           # Admin API
│   └── server.md          # Server API
├── pricing.md             # Page tarifs
└── index.md               # Page d'accueil
```

## Déploiement

### Netlify

1. Connecter le repo GitHub à Netlify
2. Configuration :
   - Build command : `npm run build`
   - Publish directory : `.vitepress/dist`
   - Base directory : `vectis-docs`

Ou via CLI :
```bash
npm install -g netlify-cli
netlify login
cd vectis-docs
npm run build
netlify deploy --prod --dir=.vitepress/dist
```

### Vercel

```bash
npm install -g vercel
cd vectis-docs
vercel
```

### GitHub Pages

Créer `.github/workflows/docs.yml` :

```yaml
name: Deploy Docs

on:
  push:
    branches: [main]
    paths:
      - 'vectis-docs/**'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
      - name: Build
        run: |
          cd vectis-docs
          npm ci
          npm run build
      - name: Deploy
        uses: peaceiris/actions-gh-pages@v3
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: vectis-docs/.vitepress/dist
```

### Docker (auto-hébergé)

```dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY vectis-docs/package*.json ./
RUN npm ci
COPY vectis-docs/ .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/.vitepress/dist /usr/share/nginx/html
EXPOSE 80
```

```bash
docker build -t vectis-docs -f Dockerfile.docs .
docker run -p 8080:80 vectis-docs
```

## Fichiers OpenAPI

Les spécifications OpenAPI sont disponibles dans `public/api/` :

- `openapi-client.yaml` - API Client (port 9081)
- `openapi-admin.yaml` - API Admin (port 9080)
- `openapi-server.yaml` - API Server (port 8080)

Ces fichiers peuvent être importés dans Postman, Insomnia, ou utilisés pour générer des clients SDK.
