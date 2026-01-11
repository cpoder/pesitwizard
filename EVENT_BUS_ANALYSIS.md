# Event Bus and WebSocket Architecture Analysis

## Executive Summary

The event bus and WebSocket implementation has several critical issues preventing proper real-time transfer status updates. The main problems are:

1. **❌ CRITICAL: WebSocket Topic Mismatch** - Frontend and backend use different topic paths
2. **❌ Redundant Event Publishing** - Dual channels (Spring Events + WebSocket) with no Spring listeners
3. **❌ Unused Service** - TransferProgressService is dead code
4. **⚠️ Synchronous Blocking** - Events published synchronously in async context
5. **⚠️ Missing Observability** - No way to track async transfer completion

## Architecture Overview

```
REST Controller (TransferController)
  └─> TransferService.sendFile() [sync]
      └─> PesitSendService.sendFileAsync() [@Async]
          └─> PesitSendService.sendFile() [sync implementation]
              └─> TransferContext [publishes events]
                  └─> TransferEventBus.publish()
                      ├─> ApplicationEventPublisher ❌ No listeners
                      └─> SimpMessagingTemplate
                          ├─> /topic/transfer/{id} ✅ Published
                          └─> /topic/transfers ✅ Published

Frontend (useTransferProgress.ts)
  └─> Subscribes to: /topic/transfer/{id}/progress ❌ MISMATCH!
```

## Critical Issues

### 1. WebSocket Topic Mismatch ❌ CRITICAL

**Backend publishes to:**
- `/topic/transfer/{transferId}` (TransferEventBus.java:33)
- `/topic/transfers` (TransferEventBus.java:35)

**Frontend subscribes to:**
- `/topic/transfer/{transferId}/progress` (useTransferProgress.ts:97)

**Impact:** Frontend NEVER receives events. The UI shows "No WebSocket updates" and falls back to polling.

**Evidence:**
```java
// TransferEventBus.java:20-21
private static final String TOPIC_TRANSFER = "/topic/transfer/";
private static final String TOPIC_ALL = "/topic/transfers";

// TransferEventBus.java:33
messagingTemplate.convertAndSend(TOPIC_TRANSFER + event.getTransferId(), event);
```

```typescript
// useTransferProgress.ts:97
const destination = `/topic/transfer/${transferId}/progress`
subscription = stompClient?.subscribe(destination, (message: IMessage) => {
```

**Fix:** Either:
- Option A: Change backend to publish to `/topic/transfer/{id}/progress`
- Option B: Change frontend to subscribe to `/topic/transfer/{id}`

### 2. Dual Event Publishing (Redundant) ❌

**Issue:** TransferEventBus publishes to BOTH Spring ApplicationEventPublisher AND WebSocket:

```java
// TransferEventBus.java:29
eventPublisher.publishEvent(event);  // ❌ No listeners exist!

// TransferEventBus.java:32-35
messagingTemplate.convertAndSend(TOPIC_TRANSFER + event.getTransferId(), event);
messagingTemplate.convertAndSend(TOPIC_ALL, event);
```

**Search Results:** No @EventListener or ApplicationListener found for TransferEvent
```bash
grep -r "@EventListener" pesitwizard-client/src/
grep -r "ApplicationListener<TransferEvent>" pesitwizard-client/src/
# No results!
```

**Impact:**
- Wasted CPU cycles publishing events to non-existent listeners
- Confusing architecture - appears to support internal listeners but doesn't
- Potential memory overhead from Spring's event multicaster

**Fix:** Remove `eventPublisher.publishEvent(event)` line - it's dead code.

### 3. Unused TransferProgressService ❌

**Issue:** TransferProgressService is injected but never used:

```java
// PesitReceiveService.java:58
private final TransferProgressService progressService;  // ❌ Never called!

// PesitReceiveService.java:347-352
private void updateTransferProgress(...) {  // ❌ Dead code - never invoked
    historyRepository.findById(historyId).ifPresent(h -> {
        h.setBytesTransferred(bytes);
        h.setLastSyncPoint(syncPoint);
        historyRepository.save(h);
    });
    // progressService methods never called here or anywhere else
}
```

**Purpose:** TransferProgressService has methods to send WebSocket messages to `/topic/transfer/{id}/progress`:
- `sendProgress()`
- `sendComplete()`
- `sendFailed()`

**Problem:** This service publishes to the CORRECT topic (`/progress` suffix) but is never invoked!

**Impact:**
- Confusing codebase - two services appear to do the same thing
- TransferProgressService has better message formatting (formatted bytes, status enum)
- Dead code increases maintenance burden

**Potential Solution Path:**
1. **Option A (Recommended):** Remove TransferProgressService entirely, fix TransferEventBus topic
2. **Option B:** Use TransferProgressService instead of TransferEventBus for WebSocket
3. **Option C:** Merge both services into unified event system

### 4. Synchronous Event Publishing in Async Context ⚠️

**Issue:** Events are published synchronously from async transfer threads:

```java
// TransferContext.java:48-50
if (eventBus != null) {
    eventBus.stateChange(transferId, prev, newState);  // Blocking call!
}

// TransferEventBus.java:32-35
messagingTemplate.convertAndSend(...);  // Synchronous send
```

**Impact:**
- If WebSocket messaging encounters issues (broker down, slow subscribers), the transfer thread blocks
- Thread pool threads (max 10) can be exhausted
- Transfer throughput degraded

**Current Thread Pool Config:**
```java
// AsyncConfig.java
Core pool: 4 threads
Max pool: 10 threads
Queue: 100 tasks
```

**Fix Options:**
1. Make event publishing async: `messagingTemplate.convertAndSendToUser()` returns void, but we can wrap in @Async
2. Use messaging template's `send()` method which can be made async
3. Use Spring's `@Async` on event publishing methods
4. Implement queue-based event buffer (more complex)

### 5. No Async Completion Tracking ⚠️

**Issue:** Async methods return void:

```java
// PesitSendService.java:53
@Async("transferExecutor")
public void sendFileAsync(...)  // void return!

// PesitReceiveService.java:65
@Async("transferExecutor")
public void receiveFileAsync(...)  // void return!
```

**Impact:**
- REST controller can't wait for completion or get result
- No way to return transfer status in response body (always returns IN_PROGRESS)
- Exception handling is limited - exceptions are lost in async context
- No integration with Spring's AsyncResult or CompletableFuture patterns

**Fix:** Return `CompletableFuture<TransferResponse>` or `Future<TransferResponse>`

### 6. Frontend Polling as Fallback ⚠️

**Frontend behavior (TransferView.vue:149-166):**
```typescript
// Polls every 2 seconds as fallback
pollingInterval = setInterval(async () => {
  const response = await api.get(`/transfers/${transferId}`)
  if (status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED') {
    stopProgressTracking()
  }
}, 2000)
```

**Impact:**
- Increased server load (polling even when WebSocket should work)
- Higher latency for UI updates (2 second interval vs real-time)
- Redundant database queries

**Root Cause:** WebSocket topic mismatch means frontend NEVER gets real-time updates, always relies on polling.

## Event Flow Analysis

### Current (Broken) Flow

```
Transfer starts
  └─> TransferContext.transition() called
      └─> TransferEventBus.publish(stateChange)
          ├─> eventPublisher.publishEvent() ❌ No one listening
          ├─> /topic/transfer/abc123 published ✅
          └─> /topic/transfers published ✅

Frontend subscribes to /topic/transfer/abc123/progress ❌
  └─> Never receives events
  └─> Falls back to polling API every 2 seconds
```

### Correct Flow (After Fix)

```
Transfer starts
  └─> TransferContext.transition() called
      └─> TransferEventBus.publish(stateChange)
          ├─> /topic/transfer/abc123 published ✅
          └─> /topic/transfers published ✅

Frontend subscribes to /topic/transfer/abc123 ✅
  └─> Receives events in real-time
  └─> No polling needed
```

## Database Update Flow

**Current state persistence:**
```java
// PesitSendService.java:105-110
historyRepository.findById(historyId).ifPresent(h -> {
    h.setStatus(TransferStatus.COMPLETED);
    h.setBytesTransferred(totalBytes);
    h.setCompletedAt(Instant.now());
    historyRepository.save(h);
});
```

**Issue:** Database updates happen in transfer thread AFTER events published.
- If database save fails, events already sent
- No transactional consistency between database state and events
- Frontend might show "completed" before database persisted

**Not critical but worth noting for future improvement.**

## WebSocket Configuration

**Current config (WebSocketConfig.java):**
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws-raw")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }
}
```

**Status:** ✅ Configuration is correct

**Frontend connection (useTransferProgress.ts:32):**
```typescript
const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws-raw`
```

**Status:** ✅ Using native WebSocket endpoint (not SockJS)

## Event Types and Usage

**TransferEvent types:**
1. `STATE_CHANGE` - Every state transition (14+ states)
2. `PROGRESS` - Throttled to 100ms intervals
3. `SYNC_POINT` - Each sync point marker
4. `ERROR` - Transfer errors
5. `COMPLETED` - Transfer completion
6. `CANCELLED` - User cancellation

**Frontend expectations (useTransferProgress.ts:4-14):**
```typescript
export interface TransferProgress {
  transferId: string
  bytesTransferred: number
  fileSize: number
  percentage: number
  lastSyncPoint: number
  status: string  // 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'
  errorMessage?: string
  bytesTransferredFormatted?: string
  fileSizeFormatted?: string
}
```

**Mismatch:** Frontend expects `TransferProgressMessage` (from TransferProgressService) but receives `TransferEvent` (from TransferEventBus).

## Recommendations

### Priority 1: Fix WebSocket Topic Mismatch (Required)

**Option A: Change Backend (Recommended)**
```java
// TransferEventBus.java
private static final String TOPIC_TRANSFER = "/topic/transfer/";
private static final String TOPIC_PROGRESS = "/progress";

public void publish(TransferEvent event) {
    if (event.getTransferId() != null) {
        messagingTemplate.convertAndSend(
            TOPIC_TRANSFER + event.getTransferId() + TOPIC_PROGRESS,
            event
        );
    }
    messagingTemplate.convertAndSend(TOPIC_ALL, event);
}
```

**Option B: Change Frontend (Alternative)**
```typescript
// useTransferProgress.ts:97
const destination = `/topic/transfer/${transferId}`  // Remove /progress suffix
```

### Priority 2: Remove Dead Code

1. Remove `eventPublisher.publishEvent(event)` from TransferEventBus.java:29
2. Remove `ApplicationEventPublisher` dependency from TransferEventBus
3. Remove `TransferProgressService` entirely OR integrate it properly
4. Remove `updateTransferProgress()` method from PesitReceiveService (lines 347-354)

### Priority 3: Make Event Publishing Async

Wrap event bus calls in async execution to prevent blocking transfer threads:

```java
@Service
public class TransferEventBus {
    @Async("eventExecutor")  // New executor for events
    public CompletableFuture<Void> publishAsync(TransferEvent event) {
        publish(event);
        return CompletableFuture.completedFuture(null);
    }
}
```

### Priority 4: Return CompletableFuture

```java
@Async("transferExecutor")
public CompletableFuture<TransferResponse> sendFileAsync(...) {
    try {
        sendFile(...);  // Existing logic
        return CompletableFuture.completedFuture(buildSuccessResponse());
    } catch (Exception e) {
        return CompletableFuture.failedFuture(e);
    }
}
```

## Testing Plan

After fixes, test:

1. **WebSocket connectivity:**
   - Start transfer
   - Verify frontend receives events on correct topic
   - Check browser console for STOMP messages

2. **Real-time progress:**
   - Transfer large file (100MB+)
   - Verify progress bar updates smoothly
   - Ensure no polling API calls when WebSocket working

3. **Event delivery:**
   - Send transfer
   - Verify state change events received
   - Verify progress events throttled correctly
   - Verify completion event received

4. **Error scenarios:**
   - Kill WebSocket connection mid-transfer
   - Verify fallback to polling works
   - Verify reconnection works

5. **Performance:**
   - Run 5 concurrent transfers
   - Verify thread pool not exhausted
   - Check event delivery latency

## File Locations

**Backend:**
- TransferEventBus: `pesitwizard-client/src/main/java/com/pesitwizard/client/event/TransferEventBus.java`
- TransferEvent: `pesitwizard-client/src/main/java/com/pesitwizard/client/event/TransferEvent.java`
- TransferProgressService: `pesitwizard-client/src/main/java/com/pesitwizard/client/service/TransferProgressService.java` ❌ UNUSED
- TransferContext: `pesitwizard-client/src/main/java/com/pesitwizard/client/pesit/TransferContext.java`
- PesitSendService: `pesitwizard-client/src/main/java/com/pesitwizard/client/pesit/PesitSendService.java`
- PesitReceiveService: `pesitwizard-client/src/main/java/com/pesitwizard/client/pesit/PesitReceiveService.java`
- AsyncConfig: `pesitwizard-client/src/main/java/com/pesitwizard/client/config/AsyncConfig.java`
- WebSocketConfig: `pesitwizard-client/src/main/java/com/pesitwizard/client/config/WebSocketConfig.java`

**Frontend:**
- useTransferProgress: `pesitwizard-client-ui/src/composables/useTransferProgress.ts`
- TransferView: `pesitwizard-client-ui/src/views/TransferView.vue`

## Conclusion

The event bus architecture is fundamentally sound but has critical implementation bugs preventing real-time updates from working. The WebSocket topic mismatch is the most critical issue - fixing this alone will enable real-time transfer status updates. Removing redundant code and making event publishing async will improve performance and maintainability.
