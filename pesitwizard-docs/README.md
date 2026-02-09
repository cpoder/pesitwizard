# PeSIT Wizard Documentation

Open source documentation for PeSIT Wizard, built with VitePress.

## Development

```bash
# Install dependencies
npm install

# Start the development server
npm run dev
```

The site will be accessible at http://localhost:5173

## Build

```bash
npm run build
```

Static files will be generated in `.vitepress/dist/`.

## Structure

```
pesitwizard-docs/
├── .vitepress/
│   └── config.ts          # VitePress configuration
├── public/
│   └── api/               # OpenAPI (OAS) files
│       ├── openapi-client.yaml
│       └── openapi-server.yaml
├── guide/
│   ├── index.md           # Introduction (What is PeSIT?)
│   ├── quickstart.md      # Quick start
│   ├── architecture.md    # Architecture
│   ├── client/            # Client documentation
│   └── server/            # Server documentation
├── api/
│   ├── index.md           # API overview
│   ├── authentication.md  # Authentication
│   ├── client.md          # Client API
│   └── server.md          # Server API
└── index.md               # Home page
```

## Deployment

### Netlify

1. Connect the GitHub repo to Netlify
2. Configuration:
   - Build command: `npm run build`
   - Publish directory: `.vitepress/dist`
   - Base directory: `pesitwizard-docs`

Or via CLI:
```bash
npm install -g netlify-cli
netlify login
cd pesitwizard-docs
npm run build
netlify deploy --prod --dir=.vitepress/dist
```

### Vercel

```bash
npm install -g vercel
cd pesitwizard-docs
vercel
```

### GitHub Pages

Create `.github/workflows/docs.yml`:

```yaml
name: Deploy Docs

on:
  push:
    branches: [main]
    paths:
      - 'pesitwizard-docs/**'

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
          cd pesitwizard-docs
          npm ci
          npm run build
      - name: Deploy
        uses: peaceiris/actions-gh-pages@v3
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: pesitwizard-docs/.vitepress/dist
```

### Docker (Self-Hosted)

A `Dockerfile` is provided in this directory:

```bash
cd pesitwizard-docs
docker build -t pesitwizard-docs .
docker run -p 8080:8080 pesitwizard-docs
```

## OpenAPI Files

OpenAPI specifications are available in `public/api/`:

- `openapi-client.yaml` - Client API (port 8080)
- `openapi-server.yaml` - Server API (port 8080)

These files can be imported into Postman, Insomnia, or used to generate SDK clients.
