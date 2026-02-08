package com.pesitwizard.client.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.pesitwizard.client.pesit.ClientState;

/**
 * Unit tests for TransferEventBus.
 * Tests the event publishing logic to both Spring events and WebSocket.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransferEventBus Unit Tests")
class TransferEventBusTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    /**
     * Synchronous executor that runs tasks immediately on the calling thread.
     * This makes tests deterministic without needing Thread.sleep().
     */
    private final Executor directExecutor = Runnable::run;

    private TransferEventBus eventBus;

    @Captor
    private ArgumentCaptor<String> destinationCaptor;

    @Captor
    private ArgumentCaptor<TransferEvent> eventCaptor;

    private static final String TEST_TRANSFER_ID = "test-transfer-123";

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(messagingTemplate, eventPublisher);
        // Construct with direct (synchronous) executor so WebSocket publishing
        // executes immediately in tests without requiring Thread.sleep()
        eventBus = new TransferEventBus(messagingTemplate, eventPublisher, directExecutor);
    }

    @Test
    @DisplayName("publish() should call both Spring event publisher and WebSocket")
    void publish_shouldCallBothChannels() {
        // Arrange
        TransferEvent event = TransferEvent.progress(TEST_TRANSFER_ID, 5000L, 10000L);

        // Act
        eventBus.publish(event);

        // Assert - Spring event should be published synchronously
        verify(eventPublisher, times(1)).publishEvent(event);

        // WebSocket publishing runs via the executor (direct/synchronous in tests)
        verify(messagingTemplate, atLeastOnce())
            .convertAndSend(any(String.class), any(TransferEvent.class));
    }

    @Test
    @DisplayName("publish() should publish to correct WebSocket topic with /progress suffix")
    void publish_shouldUseCorrectWebSocketTopic() {
        // Arrange
        TransferEvent event = TransferEvent.progress(TEST_TRANSFER_ID, 5000L, 10000L);

        // Act
        eventBus.publish(event);

        // Assert - Should publish to /topic/transfer/{id}/progress
        verify(messagingTemplate, times(1))
            .convertAndSend(eq("/topic/transfer/" + TEST_TRANSFER_ID + "/progress"), eq(event));

        // Should also publish to broadcast topic
        verify(messagingTemplate, times(1))
            .convertAndSend(eq("/topic/transfers"), eq(event));
    }

    @Test
    @DisplayName("publish() should publish to broadcast WebSocket topic")
    void publish_shouldPublishToBroadcast() {
        // Arrange
        TransferEvent event = TransferEvent.completed(TEST_TRANSFER_ID, 10000L);

        // Act
        eventBus.publish(event);

        // Assert
        verify(messagingTemplate, times(1))
            .convertAndSend(eq("/topic/transfers"), eq(event));
    }

    @Test
    @DisplayName("publish() should handle null transferId gracefully for WebSocket")
    void publish_withNullTransferId_shouldOnlyPublishToBroadcast() {
        // Arrange
        TransferEvent event = TransferEvent.builder()
            .type(TransferEvent.EventType.ERROR)
            .transferId(null)
            .errorMessage("Test error")
            .build();

        // Act
        eventBus.publish(event);

        // Assert - Should only publish to broadcast topic
        verify(messagingTemplate, times(1))
            .convertAndSend(eq("/topic/transfers"), eq(event));

        // Should NOT publish to transfer-specific topic
        verify(messagingTemplate, never())
            .convertAndSend(contains("/topic/transfer/"), any(TransferEvent.class));
    }

    @Test
    @DisplayName("publish() should not throw exception if WebSocket fails")
    void publish_shouldHandleWebSocketFailure() {
        // Arrange
        TransferEvent event = TransferEvent.error(TEST_TRANSFER_ID, "Test error", "1234");
        doThrow(new RuntimeException("WebSocket connection failed"))
            .when(messagingTemplate).convertAndSend(any(String.class), any(TransferEvent.class));

        // Act - should not throw exception
        eventBus.publish(event);

        // Assert - verify attempt was made
        verify(messagingTemplate, atLeastOnce())
            .convertAndSend(any(String.class), any(TransferEvent.class));
    }

    @Test
    @DisplayName("stateChange() should create and publish correct event")
    void stateChange_shouldPublishCorrectEvent() {
        // Arrange
        ClientState from = ClientState.CN02A_CONNECT_PENDING;
        ClientState to = ClientState.CN03_CONNECTED;

        // Act
        eventBus.stateChange(TEST_TRANSFER_ID, from, to);

        // Assert
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        TransferEvent capturedEvent = eventCaptor.getValue();

        assertEquals(TransferEvent.EventType.STATE_CHANGE, capturedEvent.getType());
        assertEquals(TEST_TRANSFER_ID, capturedEvent.getTransferId());
        assertEquals(from, capturedEvent.getPreviousState());
        assertEquals(to, capturedEvent.getCurrentState());
    }

    @Test
    @DisplayName("progress() should create and publish correct event")
    void progress_shouldPublishCorrectEvent() {
        // Arrange
        long bytes = 5000L;
        long total = 10000L;

        // Act
        eventBus.progress(TEST_TRANSFER_ID, bytes, total);

        // Assert
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        TransferEvent capturedEvent = eventCaptor.getValue();

        assertEquals(TransferEvent.EventType.PROGRESS, capturedEvent.getType());
        assertEquals(TEST_TRANSFER_ID, capturedEvent.getTransferId());
        assertEquals(bytes, capturedEvent.getBytesTransferred());
        assertEquals(total, capturedEvent.getTotalBytes());
        assertEquals(50, capturedEvent.getPercentComplete());
    }

    @Test
    @DisplayName("syncPoint() should create and publish correct event")
    void syncPoint_shouldPublishCorrectEvent() {
        // Arrange
        int syncNum = 5;
        long bytePos = 50000L;

        // Act
        eventBus.syncPoint(TEST_TRANSFER_ID, syncNum, bytePos);

        // Assert
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        TransferEvent capturedEvent = eventCaptor.getValue();

        assertEquals(TransferEvent.EventType.SYNC_POINT, capturedEvent.getType());
        assertEquals(TEST_TRANSFER_ID, capturedEvent.getTransferId());
        assertEquals(syncNum, capturedEvent.getSyncPointNumber());
        assertEquals(bytePos, capturedEvent.getSyncPointBytePosition());
    }

    @Test
    @DisplayName("error() should create and publish correct event")
    void error_shouldPublishCorrectEvent() {
        // Arrange
        String errorMessage = "Transfer failed";
        String diagCode = "1234";

        // Act
        eventBus.error(TEST_TRANSFER_ID, errorMessage, diagCode);

        // Assert
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        TransferEvent capturedEvent = eventCaptor.getValue();

        assertEquals(TransferEvent.EventType.ERROR, capturedEvent.getType());
        assertEquals(TEST_TRANSFER_ID, capturedEvent.getTransferId());
        assertEquals(errorMessage, capturedEvent.getErrorMessage());
        assertEquals(diagCode, capturedEvent.getDiagnosticCode());
    }

    @Test
    @DisplayName("completed() should create and publish correct event")
    void completed_shouldPublishCorrectEvent() {
        // Arrange
        long totalBytes = 100000L;

        // Act
        eventBus.completed(TEST_TRANSFER_ID, totalBytes);

        // Assert
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        TransferEvent capturedEvent = eventCaptor.getValue();

        assertEquals(TransferEvent.EventType.COMPLETED, capturedEvent.getType());
        assertEquals(TEST_TRANSFER_ID, capturedEvent.getTransferId());
        assertEquals(totalBytes, capturedEvent.getBytesTransferred());
    }

    @Test
    @DisplayName("cancelled() should create and publish correct event")
    void cancelled_shouldPublishCorrectEvent() {
        // Act
        eventBus.cancelled(TEST_TRANSFER_ID);

        // Assert
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        TransferEvent capturedEvent = eventCaptor.getValue();

        assertEquals(TransferEvent.EventType.CANCELLED, capturedEvent.getType());
        assertEquals(TEST_TRANSFER_ID, capturedEvent.getTransferId());
    }

    @Test
    @DisplayName("Multiple events should maintain order for Spring events")
    void multipleEvents_shouldMaintainOrderForSpringEvents() {
        // Arrange
        TransferEvent event1 = TransferEvent.stateChange(TEST_TRANSFER_ID, null, ClientState.CN02A_CONNECT_PENDING);
        TransferEvent event2 = TransferEvent.progress(TEST_TRANSFER_ID, 1000L, 10000L);
        TransferEvent event3 = TransferEvent.completed(TEST_TRANSFER_ID, 10000L);

        // Act
        eventBus.publish(event1);
        eventBus.publish(event2);
        eventBus.publish(event3);

        // Assert - Events should be published in order (Spring events are synchronous)
        verify(eventPublisher, times(3)).publishEvent(eventCaptor.capture());
        var capturedEvents = eventCaptor.getAllValues();

        assertEquals(TransferEvent.EventType.STATE_CHANGE, capturedEvents.get(0).getType());
        assertEquals(TransferEvent.EventType.PROGRESS, capturedEvents.get(1).getType());
        assertEquals(TransferEvent.EventType.COMPLETED, capturedEvents.get(2).getType());
    }
}
