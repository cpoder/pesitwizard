# PeSIT Wizard - Demo Video Recorder

Automated video demo recorder for PeSIT Wizard client UI using Playwright.
Records a complete tour of the application with the full k3d infrastructure.

## Architecture

```
                        Host Machine
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│    ┌─────────────────────────────────────────────────────┐       │
│    │              k3d Cluster (Docker)                   │       │
│    │  ┌───────────┐  ┌─────────────┐  ┌──────────────┐  │       │
│    │  │ PostgreSQL│  │   PeSIT     │  │    PeSIT     │  │       │
│    │  │  Database │◄─┤   Server    │◄─┤    Client    │  │       │
│    │  │           │  │  :30080 API │  │  :30081 API  │  │       │
│    │  │           │  │  :30500 TCP │  │              │  │       │
│    │  └───────────┘  └─────────────┘  └──────────────┘  │       │
│    └─────────────────────────────────────────────────────┘       │
│                              ▲                                   │
│                              │ HTTP Proxy                        │
│                    ┌─────────┴─────────┐                         │
│                    │   Client UI       │                         │
│                    │  :3001 (Vite)     │                         │
│                    └─────────┬─────────┘                         │
│                              ▲                                   │
│                    ┌─────────┴─────────┐                         │
│                    │   Playwright      │                         │
│                    │  (Video Recorder) │                         │
│                    └───────────────────┘                         │
└──────────────────────────────────────────────────────────────────┘
```

## Prerequisites

- **Docker Desktop** with WSL2 backend
- **k3d** installed (`curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh | bash`)
- **Node.js** 18+
- **kubectl** configured

## Quick Start

### One-Command Demo

```bash
# This will:
# 1. Setup k3d cluster (if needed)
# 2. Build and deploy all services
# 3. Start the Client UI
# 4. Record demo video
./run-demo.sh
```

The video will be saved to `./recordings/`.

### Step-by-Step

If you prefer manual control:

```bash
# 1. Setup k3d infrastructure (first time only)
cd ../scripts
./setup-k3d.sh
./build-images.sh
./deploy-k3d.sh
cd ../demo

# 2. Start the Client UI (connected to k3d Client API)
cd ../../pesitwizard-client-ui
VITE_API_URL=http://localhost:30081 VITE_PORT=3001 npm run dev &
cd ../integration-tests/demo

# 3. Record the demo
npm install
npm run demo:record
```

## Usage Options

```bash
# Full setup + record video (headless)
./run-demo.sh

# Full setup + watch live (headed browser)
./run-demo.sh --headed

# Skip infrastructure setup (if k3d already running)
./run-demo.sh --skip-infra

# Skip infra + watch live
./run-demo.sh --skip-infra --headed

# Custom URLs
SERVER_API=http://custom:30080 CLIENT_API=http://custom:30081 ./run-demo.sh
```

## Demo Scenario

The demo script showcases:

1. **Dashboard** - Overview with transfer statistics and system status
2. **Servers** - PeSIT server management (add/edit servers, TLS config)
3. **Connectors** - Storage backends (Local filesystem, SFTP, S3)
4. **Transfer** - File transfer interface with:
   - Direction selection (Send/Receive)
   - Server and partner configuration
   - Storage connection selection
   - File browser
   - Advanced options (sync points, record length)
5. **Favorites** - Saved transfer configurations for quick access
6. **Schedules** - Scheduled transfers with cron expressions
7. **Calendars** - Business calendars for scheduling
8. **History** - Transfer history with filtering and details
9. **Settings** - Application configuration

## Output

- **Format**: WebM (Playwright native) + MP4 (if ffmpeg available)
- **Resolution**: 1280x720
- **Location**: `./recordings/`

### Convert to other formats

```bash
# Convert to MP4 (done automatically if ffmpeg is installed)
ffmpeg -i recordings/video.webm -c:v libx264 -preset fast -crf 22 demo.mp4

# Convert to GIF (for README)
ffmpeg -i recordings/video.webm -vf "fps=10,scale=800:-1:flags=lanczos" -c:v gif demo.gif
```

## Customization

### Adjust timing

Edit `demo.spec.ts` and modify the `PAUSE` constants:

```typescript
const PAUSE = {
  short: 1000,    // Quick actions
  medium: 2000,   // Normal navigation
  long: 3000,     // Important screens
  veryLong: 5000, // Key features
};
```

### Enable actual file transfer

The demo fills the transfer form but doesn't execute by default. To enable:

1. Ensure server has partner "DEMO-CLIENT" with password "demo123"
2. Ensure virtual file "DEMO_FILE" is configured
3. Uncomment the transfer execution block in `demo.spec.ts`

### Add new screens

Add new sections following the existing pattern:

```typescript
// ========================================
// N. NEW FEATURE
// ========================================
console.log('N. New Feature');

await page.click('a[href="/new-feature"]');
await pause(page, PAUSE.long);
// ... interactions
```

## Troubleshooting

### k3d cluster issues

```bash
# Check cluster status
k3d cluster list

# Check pods
kubectl get pods -n pesitwizard

# View logs
kubectl logs -n pesitwizard deployment/pesitwizard-server
kubectl logs -n pesitwizard deployment/pesitwizard-client

# Restart deployment
kubectl rollout restart deployment/pesitwizard-server -n pesitwizard
```

### UI not loading

```bash
# Check if UI is running
curl http://localhost:3001

# Check API connectivity
curl http://localhost:30081/actuator/health

# Restart UI with correct API URL
cd ../../pesitwizard-client-ui
VITE_API_URL=http://localhost:30081 VITE_PORT=3001 npm run dev
```

### Playwright issues

```bash
# Install browsers
npx playwright install chromium

# Debug mode
npx playwright test demo.spec.ts --debug

# Show browser
npx playwright test demo.spec.ts --headed
```

### Video not recording

Check that the test completes successfully:

```bash
# Run with verbose output
npx playwright test demo.spec.ts --reporter=list

# Check recordings directory
ls -la recordings/
```

## Clean Up

```bash
# Stop UI (if running in background)
pkill -f "vite"

# Clean k3d cluster
cd ../scripts
./cleanup-k3d.sh
```
