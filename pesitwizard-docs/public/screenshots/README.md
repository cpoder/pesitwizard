# Documentation Screenshots

This folder contains screenshots used in the documentation.

## Structure

```
screenshots/
├── client/           # PeSIT Wizard client screenshots
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
└── admin/            # PeSIT Wizard admin screenshots
    ├── dashboard.png
    ├── clusters.png
    ├── virtual-files.png
    ├── virtual-file-placeholders.png
    ├── partners.png
    └── transfers.png
```

## How to Generate Screenshots

### Prerequisites

1. Start the services:
   ```bash
   cd scripts
   ./start-all.sh
   ```

2. Access the interfaces:
   - Client UI: http://localhost:5173
   - Admin UI: http://localhost:3000

### Screenshots to Capture

#### Client UI (http://localhost:5173)

| File | Page | Description |
|------|------|-------------|
| `dashboard.png` | Dashboard | Overview with statistics |
| `transfer-send.png` | Transfer | SEND form with filled fields |
| `transfer-receive.png` | Transfer | RECEIVE form with placeholders |
| `path-placeholders.png` | Transfer | Placeholders component with visible tags |
| `favorites.png` | Favorites | List of favorites with cards |
| `favorite-edit.png` | Favorites | Edit favorite modal |
| `schedules.png` | Schedules | List of scheduled transfers |
| `schedule-create.png` | Favorites | Create schedule modal |
| `calendars.png` | Calendars | List of business calendars |
| `tls-config-nav.png` | Navigation | Sidebar with TLS Config highlighted |
| `tls-import-truststore.png` | TLS Config | Import truststore (CA) modal |
| `tls-import-keystore.png` | TLS Config | Import keystore (client cert) modal |
| `tls-enabled.png` | TLS Config | View with TLS enabled and certificates configured |
| `tls-status.png` | TLS Config | TLS status with certificate information |

#### Admin UI (http://localhost:3000)

| File | Page | Description |
|------|------|-------------|
| `dashboard.png` | Dashboard | Cluster overview |
| `clusters.png` | Clusters | List of clusters |
| `virtual-files.png` | Virtual Files | List of virtual files |
| `virtual-file-placeholders.png` | Virtual Files | Form with placeholders |
| `partners.png` | Partners | List of partners |
| `transfers.png` | Transfers | Transfer history |

### Screenshot Guidelines

1. **Resolution**: 1280x800 minimum
2. **Format**: PNG
3. **Content**: Use realistic data (e.g., "BNP Paribas", "SEPA_TRANSFERS")
4. **Theme**: Light mode
5. **Cropping**: Avoid browser toolbars

### Recommended Tools

- **macOS**: Cmd+Shift+4 then Space to capture a window
- **Linux**: Flameshot, GNOME Screenshot
- **Windows**: Snipping Tool, ShareX
- **Browser**: "Full Page Screen Capture" extension
