package com.pesitwizard.client.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.pesitwizard.client.pesit.ClientState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Central event bus for PeSIT transfer events.
 *
 * <p>This class publishes events to two channels:
 * <ul>
 *   <li><b>Spring ApplicationEventPublisher</b> - For pluggable integrations
 *       (Kafka, monitoring systems, metrics collectors, etc.). Plugins should
 *       implement {@code @EventListener} for {@link TransferEvent}. Published
 *       synchronously to maintain order.</li>
 *   <li><b>WebSocket topics</b> - For real-time UI updates. Published
 *       asynchronously to prevent blocking transfer threads if WebSocket
 *       encounters issues.</li>
 * </ul>
 *
 * <h3>Creating a Plugin</h3>
 * <p>To monitor transfer events, create a Spring bean with {@code @EventListener}:
 * <pre>{@code
 * @Component
 * public class KafkaTransferPlugin {
 *
 *     @EventListener
 *     @Async("pluginExecutor")  // Optional: make plugin async
 *     public void onTransferEvent(TransferEvent event) {
 *         // Send to Kafka, metrics system, logging, etc.
 *         switch (event.getType()) {
 *             case PROGRESS -> handleProgress(event);
 *             case COMPLETED -> handleCompleted(event);
 *             case ERROR -> handleError(event);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Important:</b> Synchronous event listeners should be fast (< 50ms). For
 * long-running operations, use {@code @Async("pluginExecutor")} or
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} to process events
 * after database transactions complete.
 *
 * <h3>WebSocket Topics</h3>
 * <ul>
 *   <li>{@code /topic/transfer/{transferId}/progress} - Individual transfer events</li>
 *   <li>{@code /topic/transfers} - All transfer events (broadcast)</li>
 * </ul>
 *
 * @see TransferEvent
 * @see org.springframework.context.event.EventListener
 * @see org.springframework.transaction.event.TransactionalEventListener
 * @see org.springframework.scheduling.annotation.Async
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventBus {

    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    private static final String TOPIC_TRANSFER = "/topic/transfer/";
    private static final String TOPIC_PROGRESS_SUFFIX = "/progress";
    private static final String TOPIC_ALL = "/topic/transfers";

    /**
     * Publishes a transfer event to both Spring event system and WebSocket topics.
     *
     * @param event the transfer event to publish
     */
    public void publish(TransferEvent event) {
        log.debug("Event: {} - {} for transfer {}", event.getType(),
                event.getCurrentState() != null ? event.getCurrentState().getCode() : "",
                event.getTransferId());

        // Publish to Spring event system for plugins (synchronous to maintain order)
        // Plugins can use @Async("pluginExecutor") to process asynchronously
        eventPublisher.publishEvent(event);

        // Publish to WebSocket asynchronously to avoid blocking transfer threads
        publishToWebSocketAsync(event);
    }

    /**
     * Publishes event to WebSocket topics asynchronously.
     * Separate method with @Async to prevent WebSocket issues from blocking transfer threads.
     *
     * @param event the event to publish
     */
    @Async("websocketExecutor")
    public void publishToWebSocketAsync(TransferEvent event) {
        try {
            // Publish to transfer-specific topic with /progress suffix
            if (event.getTransferId() != null) {
                String destination = TOPIC_TRANSFER + event.getTransferId() + TOPIC_PROGRESS_SUFFIX;
                messagingTemplate.convertAndSend(destination, event);
            }

            // Publish to broadcast topic
            messagingTemplate.convertAndSend(TOPIC_ALL, event);
        } catch (Exception e) {
            // Log error but don't fail transfer if WebSocket has issues
            log.error("Failed to publish WebSocket message for transfer {}: {}",
                    event.getTransferId(), e.getMessage(), e);
        }
    }

    /**
     * Publishes a state change event.
     *
     * @param transferId the transfer identifier
     * @param from the previous state
     * @param to the new state
     */
    public void stateChange(String transferId, ClientState from, ClientState to) {
        publish(TransferEvent.stateChange(transferId, from, to));
    }

    /**
     * Publishes a progress event.
     *
     * @param transferId the transfer identifier
     * @param bytes bytes transferred so far
     * @param total total bytes to transfer
     */
    public void progress(String transferId, long bytes, long total) {
        publish(TransferEvent.progress(transferId, bytes, total));
    }

    /**
     * Publishes a sync point event.
     *
     * @param transferId the transfer identifier
     * @param syncNum sync point number
     * @param bytePos byte position at sync point
     */
    public void syncPoint(String transferId, int syncNum, long bytePos) {
        publish(TransferEvent.syncPoint(transferId, syncNum, bytePos));
    }

    /**
     * Publishes an error event.
     *
     * @param transferId the transfer identifier
     * @param message error message
     * @param diagCode diagnostic code
     */
    public void error(String transferId, String message, String diagCode) {
        publish(TransferEvent.error(transferId, message, diagCode));
    }

    /**
     * Publishes a completion event.
     *
     * @param transferId the transfer identifier
     * @param totalBytes total bytes transferred
     */
    public void completed(String transferId, long totalBytes) {
        publish(TransferEvent.completed(transferId, totalBytes));
    }

    /**
     * Publishes a cancellation event.
     *
     * @param transferId the transfer identifier
     */
    public void cancelled(String transferId) {
        publish(TransferEvent.cancelled(transferId));
    }
}
