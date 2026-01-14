# Changelog

All notable changes to PeSIT Wizard will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **File Browser API** (`/api/v1/filesystem/browse`) - Navigate server filesystem from Admin UI
- **Version Info at Startup** - Logs version, build timestamp, and commit hash on startup
- **Kubernetes JGroups Config** (`kubernetes.xml`) - DNS_PING based discovery for better K8s support

### Fixed
- **Virtual Folders Access Denied** - Changed default paths from `./received` to `/data/received` (PVC mount point)
- **Regex Exception with `*` Wildcard** - Fixed glob-to-regex conversion in `allowedFiles` (escape dots, `*` → `.*`)
- **Proper Error Handling** - Access denied errors now return clean PeSIT ABORT with DiagnosticCode.D2_211 instead of crashing
- **Multiple Leaders Bug** - Switched from TCPPING to DNS_PING for reliable Kubernetes cluster discovery

### Changed
- JGroups config for K8s deployments now uses `kubernetes.xml` with DNS_PING instead of TCPPING

## [1.0.0] - 2026-01-14

### Features
- PeSIT Hors-SIT protocol implementation (TCP/IP)
- High Availability with JGroups clustering
- Partner management with authentication
- Virtual file mappings with placeholders
- Transfer history and monitoring
- REST API for configuration
- PostgreSQL support for production
- OpenTelemetry integration (metrics & tracing)
- Kubernetes deployment support
- Leader election with pod label updates

---

## Migration Notes

### Upgrading to latest

1. **Virtual Folder Paths**: If you have custom virtual folder configurations, ensure paths are under `/data/` (e.g., `/data/received`, `/data/send`)

2. **Allowed Files Patterns**: The `*` wildcard now works correctly. Patterns like `*.txt` or `*` are supported.

3. **Cluster Redeployment**: After upgrading, perform a force redeploy to apply the new JGroups configuration:
   ```
   POST /api/v1/clusters/{id}/deploy?force=true
   ```

4. **Version Verification**: Check logs at startup for version banner:
   ```
   ╔════════════════════════════════════════════════════════════╗
   ║            PeSIT Wizard Server                             ║
   ╠════════════════════════════════════════════════════════════╣
   ║  Version:     1.0.0                                        ║
   ║  Build:       2024-12-20                                   ║
   ║  Commit:      abc1234                                      ║
   ╚════════════════════════════════════════════════════════════╝
   ```
