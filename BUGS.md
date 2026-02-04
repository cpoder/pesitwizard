# Bugs to Fix

## Client UI

### 1. ~~500 Errors Without Clear Messages~~ FIXED
**Priority**: High
**Component**: pesitwizard-client-api / pesitwizard-client-ui
**Status**: Fixed (API side) - UI still needs to be updated to display messages

**Solution implemented**:
- Added `GlobalExceptionHandler` with `@RestControllerAdvice`
- Now returns 409 Conflict for unique constraint violations
- Returns 400 Bad Request for validation errors
- Stack traces are no longer exposed to users
- Configuration `server.error.include-stacktrace: never` added

**Files created/modified**:
- `pesitwizard-client/src/main/java/com/pesitwizard/client/exception/GlobalExceptionHandler.java`
- `pesitwizard-client/src/main/java/com/pesitwizard/client/exception/ApiError.java`
- `pesitwizard-client/src/main/resources/application.yml`

**Error response format**:
```json
{
  "timestamp": "2026-01-29T20:00:00Z",
  "status": 409,
  "error": "CONFLICT",
  "message": "A resource with name 'Production Calendar' already exists",
  "path": "/api/v1/calendars"
}
```

**Note**: The UI still needs to be updated to display these messages in popups.

---

### 2. ~~Hourly Scheduling Bug~~ FIXED
**Priority**: High
**Component**: pesitwizard-client-api (ScheduleService)
**Status**: Fixed

When creating a daily schedule at 9:30 AM, the transfer was running 24 hours after the current time instead of running the next day at 9:30 AM.

**Solution implemented**:
- Added `calculateInitialNextRunTime()` method in `TransferSchedulerService`
- For DAILY, WEEKLY, MONTHLY schedules: now uses the configured `dailyTime`
- If today's scheduled time has already passed, schedules for the next day
- Same logic applied for WEEKLY (next matching weekday) and MONTHLY (next month)

**File modified**:
- `pesitwizard-client/src/main/java/com/pesitwizard/client/service/TransferSchedulerService.java`

---

## Client API (RECEIVE)

### 3. ~~EOFException During RECEIVE Transfers~~ FIXED
**Priority**: Medium
**Component**: pesitwizard-client-api (PesitReceiveService)
**Status**: Fixed

**Root cause identified**:
During refactoring `e0642d3` (extraction of `TransferService` into `PesitReceiveService`), sending `TRANS.END` was accidentally removed from the cleanup sequence.

**Correct sequence** (original working code):
```
TRANS_END -> ACK_TRANS_END -> CLOSE -> ACK_CLOSE -> DESELECT -> ACK_DESELECT -> RELEASE -> ACK_RELEASE
```

**Buggy sequence** (after refactoring):
```
CLOSE -> ACK_CLOSE -> ... (server closes because TRANS_END was never received)
```

**Solution implemented**:
- Added `TRANS.END` before `CLOSE` in `sendCleanupFpdus()`
- Realigned with the correct PeSIT sequence

**File modified**:
- `pesitwizard-client/src/main/java/com/pesitwizard/client/pesit/PesitReceiveService.java`

---

### 4. ~~Encryption Salt Not Shared Between Server Pods~~ FIXED
**Priority**: Critical
**Component**: pesitwizard-server (k8s deployment)
**Status**: Fixed

Each pod in the StatefulSet was generating its own AES encryption salt at startup. Partners created on one pod could not be authenticated on another pod because the password could not be decrypted.

**Solution implemented**:
- Added support for the `PESITWIZARD_SECURITY_ENCRYPTION_SALT` environment variable
- The salt can now be shared via a Kubernetes Secret (base64-encoded, 32 bytes)
- Helm chart configuration: `config.security.encryptionSalt`
- Generate with: `openssl rand -base64 32`

**Files modified**:
- `pesitwizard-security/src/main/java/com/pesitwizard/security/AesSecretsProvider.java`
- `pesitwizard-security/src/main/java/com/pesitwizard/security/SecretsConfig.java`
- `pesitwizard-helm-charts/pesitwizard-server/values.yaml`
- `pesitwizard-helm-charts/pesitwizard-server/templates/secrets.yaml`
- `pesitwizard-helm-charts/pesitwizard-server/templates/deployment.yaml`

---

### 5. ~~Transfer Progress Bar Not Working~~ FIXED
**Priority**: High
**Component**: pesitwizard-client-ui / WebSocket
**Status**: Fixed

**Root cause identified**:
- Incompatibility between the backend `TransferEvent` structure and the frontend `TransferProgress` interface
- Backend was sending: `type`, `totalBytes`, `percentComplete`
- Frontend was expecting: `status`, `fileSize`, `percentage`

**Solution implemented**:
- Added a `TransferEventPayload` interface to represent the backend event
- Added a `normalizeEvent()` function to convert backend fields to frontend format
- Mapping of `type` (enum) to `status` (string)
- Mapping of `totalBytes` to `fileSize`, `percentComplete` to `percentage`
- Added byte formatting in KB/MB/GB

**File modified**:
- `pesitwizard-client-ui/src/composables/useTransferProgress.ts`

---

### 6. ~~EOFException During SEND of Large Files~~ FIXED
**Priority**: High
**Component**: pesitwizard-client-api / pesitwizard-server
**Status**: Fixed

**Root cause identified**:
- The sync point interval was hardcoded at 10KB (`syncIntervalKb = 10`)
- For a 50MB file, this created ~5000 sync points
- Each sync point requires an ACK_SYN from the server
- The network overhead caused timeouts leading to EOFException

**Solution implemented**:
- Using `config.getSyncPointInterval()` instead of the hardcoded value
- The default value in TransferConfig is 100KB (10x fewer sync points)
- For a 50MB file: ~500 sync points instead of ~5000

**File modified**:
- `pesitwizard-client/src/main/java/com/pesitwizard/client/pesit/PesitSendService.java`

**Note**: Additional testing with 50MB+ files is recommended to fully validate this fix.

---

## Notes

These bugs were identified during the creation of the demo video (January 2026).

## Fix History

- **2026-01-29**: Fixed all 6 identified bugs (commits bd72aff, 03196ec and following)
