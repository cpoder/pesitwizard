# Event Bus Fix Plan - Maintaining Pluggable Architecture

## Design Goals

1. ✅ Keep Spring ApplicationEventPublisher for third-party plugins (Kafka, monitoring, etc.)
2. ✅ Fix WebSocket topic mismatch for real-time UI updates
3. ✅ Remove redundant TransferProgressService
4. ✅ Make WebSocket publishing async to avoid blocking transfer threads
5. ✅ Add async completion tracking
6. ✅ Document pluggable architecture for future developers

## Architecture

```
Transfer Thread
  └─> TransferContext.transition()
      └─> TransferEventBus.publish()
          ├─> Spring Events (sync) ✅ For plugins/monitoring
          │   └─> @EventListener beans (Kafka, metrics, etc.)
          └─> WebSocket (async) ✅ For real-time UI
              └─> /topic/transfer/{id} OR /topic/transfer/{id}/progress
```

## Fixes to Implement

### Fix 1: WebSocket Topic Mismatch (CRITICAL)

**Problem:** Backend publishes to `/topic/transfer/{id}`, frontend subscribes to `/topic/transfer/{id}/progress`

**Solution:** Add `/progress` suffix to backend topic

**File:** `TransferEventBus.java`

```java
private static final String TOPIC_TRANSFER = "/topic/transfer/";
private static final String TOPIC_PROGRESS_SUFFIX = "/progress";  // NEW
private static final String TOPIC_ALL = "/topic/transfers";

public void publish(TransferEvent event) {
    // Spring events for plugins (synchronous - plugins should be fast)
    eventPublisher.publishEvent(event);

    // WebSocket for UI (async to avoid blocking transfer threads)
    publishToWebSocketAsync(event);
}

@Async("websocketExecutor")  // NEW: Separate executor for WebSocket
public void publishToWebSocketAsync(TransferEvent event) {
    try {
        if (event.getTransferId() != null) {
            String destination = TOPIC_TRANSFER + event.getTransferId() + TOPIC_PROGRESS_SUFFIX;
            messagingTemplate.convertAndSend(destination, event);
        }
        messagingTemplate.convertAndSend(TOPIC_ALL, event);
    } catch (Exception e) {
        log.error("Failed to publish WebSocket message for transfer {}: {}",
            event.getTransferId(), e.getMessage());
    }
}
```

### Fix 2: Add Dedicated WebSocket Executor

**File:** `AsyncConfig.java`

```java
@Bean(name = "websocketExecutor")
public Executor websocketExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("websocket-");
    executor.initialize();
    return executor;
}
```

**Rationale:** Separate executor prevents WebSocket issues from blocking transfer threads.

### Fix 3: Remove TransferProgressService

**Problem:** Completely unused service with duplicate functionality.

**Solution:** Delete the file and remove all references.

**Files to modify:**
- Delete: `TransferProgressService.java`
- Modify: `PesitReceiveService.java` - Remove injection and `updateTransferProgress()` method

### Fix 4: Add Async Completion Tracking

**File:** `PesitSendService.java`

```java
@Async("transferExecutor")
public CompletableFuture<Void> sendFileAsync(
        TransferRequest request, String historyId, PesitServer server,
        TransferConfig config, long fileSize, String correlationId,
        Set<String> cancelledTransfers) {

    return CompletableFuture.runAsync(() -> {
        Observation.createNotStarted("pesit.send", observationRegistry)
            .lowCardinalityKeyValue("pesit.direction", "SEND")
            .highCardinalityKeyValue("pesit.server", request.getServer())
            .highCardinalityKeyValue("correlation.id", correlationId)
            .observe(() -> sendFile(request, historyId, server, config,
                fileSize, correlationId, cancelledTransfers));
    }, taskExecutor());
}
```

**File:** `PesitReceiveService.java` - Similar change

### Fix 5: Add Plugin Architecture Documentation

**File:** `TransferEventBus.java` - Add JavaDoc

```java
/**
 * Central event bus for PeSIT transfer events.
 *
 * <p>This class publishes events to two channels:
 * <ul>
 *   <li><b>Spring ApplicationEventPublisher</b> - For pluggable integrations
 *       (Kafka, monitoring systems, metrics collectors, etc.). Plugins should
 *       implement {@code @EventListener} for {@link TransferEvent}.</li>
 *   <li><b>WebSocket topics</b> - For real-time UI updates. Published
 *       asynchronously to prevent blocking transfer threads.</li>
 * </ul>
 *
 * <h3>Creating a Plugin</h3>
 * <pre>{@code
 * @Component
 * public class KafkaTransferPlugin {
 *     @EventListener
 *     public void onTransferEvent(TransferEvent event) {
 *         // Send to Kafka, metrics system, etc.
 *     }
 * }
 * }</pre>
 *
 * <p><b>Important:</b> Event listeners should be fast (< 50ms). For long-running
 * operations, use {@code @Async} or {@code @TransactionalEventListener} with
 * {@code phase = AFTER_COMMIT}.
 *
 * @see TransferEvent
 * @see org.springframework.context.event.EventListener
 * @see org.springframework.transaction.event.TransactionalEventListener
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventBus {
```

### Fix 6: Add Example Plugin

**New File:** `example/KafkaTransferEventPlugin.java.example`

```java
package com.pesitwizard.client.example;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import com.pesitwizard.client.event.TransferEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * Example plugin that sends transfer events to Kafka.
 *
 * To enable: Remove .example extension and add Kafka dependencies.
 */
@Slf4j
// @Component  // Uncomment to enable
public class KafkaTransferEventPlugin {

    // @Autowired
    // private KafkaTemplate<String, TransferEvent> kafkaTemplate;

    @Async("pluginExecutor")
    @EventListener
    public void onTransferEvent(TransferEvent event) {
        log.debug("Publishing transfer event to Kafka: {} - {}",
            event.getTransferId(), event.getType());

        // kafkaTemplate.send("transfer-events", event.getTransferId(), event);
    }
}
```

### Fix 7: Add Plugin Executor

**File:** `AsyncConfig.java`

```java
@Bean(name = "pluginExecutor")
public Executor pluginExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(5);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("plugin-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
}
```

**Rationale:** Plugins can use `@Async("pluginExecutor")` to avoid blocking main event publishing.

### Fix 8: Add @TransactionalEventListener Support

For plugins that need to wait for database commit before processing:

**Example:**
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onTransferCompleted(TransferEvent event) {
    if (event.getType() == EventType.COMPLETED) {
        // Send notification after DB commit confirmed
    }
}
```

## Implementation Order

1. ✅ Add `websocketExecutor` to AsyncConfig
2. ✅ Modify TransferEventBus to use async WebSocket publishing
3. ✅ Fix WebSocket topic to include `/progress` suffix
4. ✅ Delete TransferProgressService
5. ✅ Remove TransferProgressService references from PesitReceiveService
6. ✅ Add JavaDoc to TransferEventBus
7. ✅ Create example plugin file
8. ✅ Add `pluginExecutor` to AsyncConfig
9. ✅ Update CLAUDE.md with plugin architecture section
10. ✅ Test WebSocket connectivity

## Testing Plan

### 1. WebSocket Functionality
```bash
# Start backend
mvn spring-boot:run

# Start frontend
cd pesitwizard-client-ui
npm run dev

# Test transfer and check browser console for WebSocket messages
```

### 2. Plugin Architecture
```java
// Create test plugin
@Component
@Slf4j
public class TestTransferEventPlugin {
    @EventListener
    public void onEvent(TransferEvent event) {
        log.info("Plugin received: {} - {}", event.getType(), event.getTransferId());
    }
}

// Run transfer and check logs
```

### 3. Performance
```bash
# Run 5 concurrent transfers
# Check thread pool metrics
# Verify WebSocket async publishing doesn't block transfers
```

## Benefits

1. **Real-time UI Updates** - WebSocket works correctly
2. **Pluggable Architecture** - Easy to add Kafka, metrics, monitoring
3. **Thread Safety** - WebSocket issues don't block transfers
4. **Clean Code** - Remove unused service
5. **Documentation** - Clear examples for future plugins
6. **Async Completion** - REST layer can track async operations

## Migration Notes

### For Plugin Developers

If you want to monitor transfer events:

```java
@Component
public class MyPlugin {
    @EventListener
    @Async("pluginExecutor")  // Optional: makes your plugin async
    public void onTransferEvent(TransferEvent event) {
        switch (event.getType()) {
            case STATE_CHANGE -> handleStateChange(event);
            case PROGRESS -> handleProgress(event);
            case COMPLETED -> handleCompleted(event);
            case ERROR -> handleError(event);
        }
    }
}
```

### WebSocket Event Format

Frontend now receives on `/topic/transfer/{id}/progress`:

```json
{
  "transferId": "abc-123",
  "type": "PROGRESS",
  "timestamp": "2026-01-11T20:00:00Z",
  "bytesTransferred": 1048576,
  "totalBytes": 5242880,
  "percentComplete": 20,
  "currentState": "TDE02A_SENDING_DATA"
}
```

## Summary of Changes

| Component | Action | Reason |
|-----------|--------|--------|
| TransferEventBus | Add async WebSocket publishing | Prevent blocking transfer threads |
| TransferEventBus | Fix topic to `/progress` suffix | Match frontend subscription |
| TransferEventBus | Add JavaDoc | Document plugin architecture |
| AsyncConfig | Add websocketExecutor | Separate WebSocket thread pool |
| AsyncConfig | Add pluginExecutor | Optional executor for plugins |
| TransferProgressService | **DELETE** | Unused, redundant |
| PesitReceiveService | Remove progressService | Clean up dead code |
| PesitSendService | Return CompletableFuture | Enable async tracking |
| PesitReceiveService | Return CompletableFuture | Enable async tracking |
| Create example plugin | Add KafkaTransferEventPlugin.example | Show plugin pattern |
