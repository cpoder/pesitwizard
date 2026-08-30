# PeSIT Wizard Documentation

Open-source documentation for PeSIT Wizard (the Rust node), built with VitePress. Deployed at
**docs.pesitwizard.com** via Cloudflare Pages, which builds this directory automatically on push.

## Development

```bash
npm install
npm run dev      # http://localhost:5173
```

## Build

```bash
npm run build    # static output in .vitepress/dist/
```

## Structure

```
pesitwizard-docs/
├── .vitepress/
│   └── config.ts              # VitePress configuration (nav / sidebar)
├── public/
│   └── screenshots/           # web-console screenshots
├── guide/
│   ├── index.md               # What is PeSIT Wizard?
│   ├── quickstart.md          # Quick start
│   ├── architecture.md        # Architecture (crates, unified node)
│   ├── deployment.md          # Docker / Kubernetes
│   ├── connect-express.md     # Connect:Express interoperability
│   ├── troubleshooting.md
│   ├── client/usage.md        # Partners & transfers
│   ├── server/                # the node: installation, configuration,
│   │                          #   connectors, security, clustering, observability
│   └── reference/protocol.md  # PeSIT E protocol reference
├── api/
│   └── index.md               # REST API reference (admin + transfer)
└── index.md                   # Home page
```

## Deployment

Cloudflare Pages builds this directory on every push to `main`:

- Root directory: `pesitwizard-docs`
- Build command: `npm run build`
- Output directory: `.vitepress/dist`
