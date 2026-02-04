# PeSIT Wizard Client UI

Web interface for performing file transfers via the PeSIT protocol.

## Features

- **File Sending**: Send files to a PeSIT server
- **File Receiving**: Retrieve files from a PeSIT server
- **Server Management**: Configure multiple target PeSIT servers
- **History**: View transfer history
- **Connection Test**: Verify connectivity with a server

## Prerequisites

- Node.js 18+
- npm, yarn, or pnpm
- Backend `pesitwizard-client` running (port 8080)

## Installation

```bash
npm install
```

## Development

```bash
npm run dev
```

The application will be accessible at http://localhost:3001

## Production Build

```bash
npm run build
```

## Configuration

The backend URL is configured via `VITE_API_URL`. Default: `http://localhost:8080`.

## Tech Stack

- Vue 3 + TypeScript
- Vuetify 3
- Vite
- Pinia
