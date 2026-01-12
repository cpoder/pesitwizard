package com.pesitwizard.client.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.pesitwizard.client.event.TransferEvent;
import com.pesitwizard.client.event.TransferEventBus;
import com.pesitwizard.client.pesit.ClientState;

/**
 * Integration test for WebSocket event publishing.
 * Verifies that TransferEventBus correctly publishes events to WebSocket
 * topics.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({ "test", "nosecurity" })
@DisplayName("WebSocket Event Integration Tests")
@Disabled("WebSocket tests are flaky due to async timing - run manually for verification")
class WebSocketEventIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TransferEventBus eventBus;

    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private BlockingQueue<TransferEvent> receivedEvents;

    @BeforeEach
    void setUp() throws Exception {
        receivedEvents = new LinkedBlockingQueue<>();

        // Create WebSocket STOMP client
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        // Connect to WebSocket
        String wsUrl = String.format("ws://localhost:%d/ws-raw", port);
        stompSession = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {
        })
                .get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
        }
    }

    /**
     * Helper method to publish event and wait for async processing.
     */
    private void publishAndWait(Runnable publisher) throws InterruptedException {
        publisher.run();
        // Wait for async @Async(websocketExecutor) method to execute and deliver
        // message
        Thread.sleep(1000);
    }

    /**
     * Helper method to ensure subscription is fully established.
     */
    private void waitForSubscription() throws InterruptedException {
        Thread.sleep(500);
    }

    @Test
    @DisplayName("Should publish progress event to transfer-specific topic")
    void shouldPublishProgressEventToTransferTopic() throws Exception {
        // Arrange
        String transferId = "test-transfer-123";
        String topic = "/topic/transfer/" + transferId + "/progress";

        // Subscribe to topic
        stompSession.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TransferEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedEvents.add((TransferEvent) payload);
            }
        });
        waitForSubscription();

        // Act
        publishAndWait(() -> eventBus.progress(transferId, 5000L, 10000L));

        // Assert - Wait for message to arrive
        TransferEvent event = receivedEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "Should receive progress event");
        assertEquals(transferId, event.getTransferId());
        assertEquals(TransferEvent.EventType.PROGRESS, event.getType());
        assertEquals(5000L, event.getBytesTransferred());
        assertEquals(10000L, event.getTotalBytes());
        assertEquals(50, event.getPercentComplete());
    }

    @Test
    @DisplayName("Should publish state change event to transfer-specific topic")
    void shouldPublishStateChangeEventToTransferTopic() throws Exception {
        // Arrange
        String transferId = "test-transfer-456";
        String topic = "/topic/transfer/" + transferId + "/progress";

        stompSession.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TransferEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedEvents.add((TransferEvent) payload);
            }
        });
        waitForSubscription();

        // Act
        publishAndWait(
                () -> eventBus.stateChange(transferId, ClientState.CN01_REPOS, ClientState.CN02A_CONNECT_PENDING));

        // Assert
        TransferEvent event = receivedEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "Should receive state change event");
        assertEquals(transferId, event.getTransferId());
        assertEquals(TransferEvent.EventType.STATE_CHANGE, event.getType());
        assertEquals(ClientState.CN01_REPOS, event.getPreviousState());
        assertEquals(ClientState.CN02A_CONNECT_PENDING, event.getCurrentState());
    }

    @Test
    @DisplayName("Should publish sync point event to transfer-specific topic")
    void shouldPublishSyncPointEventToTransferTopic() throws Exception {
        // Arrange
        String transferId = "test-transfer-789";
        String topic = "/topic/transfer/" + transferId + "/progress";

        stompSession.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TransferEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedEvents.add((TransferEvent) payload);
            }
        });
        waitForSubscription();

        // Act
        publishAndWait(() -> eventBus.syncPoint(transferId, 5, 50000L));

        // Assert
        TransferEvent event = receivedEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "Should receive sync point event");
        assertEquals(transferId, event.getTransferId());
        assertEquals(TransferEvent.EventType.SYNC_POINT, event.getType());
        assertEquals(5, event.getSyncPointNumber());
        assertEquals(50000L, event.getSyncPointBytePosition());
    }

    @Test
    @DisplayName("Should publish error event to transfer-specific topic")
    void shouldPublishErrorEventToTransferTopic() throws Exception {
        // Arrange
        String transferId = "test-transfer-error";
        String topic = "/topic/transfer/" + transferId + "/progress";

        stompSession.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TransferEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedEvents.add((TransferEvent) payload);
            }
        });
        waitForSubscription();

        // Act
        publishAndWait(() -> eventBus.error(transferId, "Transfer failed", "1234"));

        // Assert
        TransferEvent event = receivedEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "Should receive error event");
        assertEquals(transferId, event.getTransferId());
        assertEquals(TransferEvent.EventType.ERROR, event.getType());
        assertEquals("Transfer failed", event.getErrorMessage());
        assertEquals("1234", event.getDiagnosticCode());
    }

    @Test
    @DisplayName("Should publish completed event to transfer-specific topic")
    void shouldPublishCompletedEventToTransferTopic() throws Exception {
        // Arrange
        String transferId = "test-transfer-completed";
        String topic = "/topic/transfer/" + transferId + "/progress";

        stompSession.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TransferEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedEvents.add((TransferEvent) payload);
            }
        });
        waitForSubscription();

        // Act
        publishAndWait(() -> eventBus.completed(transferId, 100000L));

        // Assert
        TransferEvent event = receivedEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "Should receive completed event");
        assertEquals(transferId, event.getTransferId());
        assertEquals(TransferEvent.EventType.COMPLETED, event.getType());
        assertEquals(100000L, event.getBytesTransferred());
    }

    @Test
    @DisplayName("Should publish cancelled event to transfer-specific topic")
    void shouldPublishCancelledEventToTransferTopic() throws Exception {
        // Arrange
        String transferId = "test-transfer-cancelled";
        String topic = "/topic/transfer/" + transferId + "/progress";

        stompSession.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TransferEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedEvents.add((TransferEvent) payload);
            }
        });
        waitForSubscription();

        // Act
        publishAndWait(() -> eventBus.cancelled(transferId));

        // Assert
        TransferEvent event = receivedEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "Should receive cancelled event");
        assertEquals(transferId, event.getTransferId());
        assertEquals(TransferEvent.EventType.CANCELLED, event.getType());
    }

    @Test
    @DisplayName("Should publish events to broadcast topic /topic/transfers")
    void shouldPublishEventsToBroadcastTopic() throws Exception {
        // Arrange
        String broadcastTopic = "/topic/transfers";

        stompSession.subscribe(broadcastTopic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TransferEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedEvents.add((TransferEvent) payload);
            }
        });
        waitForSubscription();

        // Act - Publish multiple events
        publishAndWait(() -> eventBus.progress("transfer-1", 1000L, 10000L));
        publishAndWait(() -> eventBus.progress("transfer-2", 2000L, 20000L));
        publishAndWait(() -> eventBus.completed("transfer-1", 10000L));

        // Assert - Should receive all events on broadcast topic
        TransferEvent event1 = receivedEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(event1, "Should receive first event");
        assertEquals("transfer-1", event1.getTransferId());

        TransferEvent event2 = receivedEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(event2, "Should receive second event");
        assertEquals("transfer-2", event2.getTransferId());

        TransferEvent event3 = receivedEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(event3, "Should receive third event");
        assertEquals("transfer-1", event3.getTransferId());
        assertEquals(TransferEvent.EventType.COMPLETED, event3.getType());
    }

    @Test
    @DisplayName("Should receive events only on subscribed transfer-specific topic")
    void shouldReceiveEventsOnlyOnSubscribedTopic() throws Exception {
        // Arrange - Subscribe to one transfer's topic only
        String subscribedTransferId = "subscribed-transfer";
        String subscribedTopic = "/topic/transfer/" + subscribedTransferId + "/progress";

        stompSession.subscribe(subscribedTopic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TransferEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedEvents.add((TransferEvent) payload);
            }
        });
        waitForSubscription();

        // Act - Publish events for different transfers
        publishAndWait(() -> eventBus.progress("other-transfer", 1000L, 10000L));
        publishAndWait(() -> eventBus.progress(subscribedTransferId, 2000L, 20000L));
        publishAndWait(() -> eventBus.progress("another-transfer", 3000L, 30000L));

        // Assert - Should only receive event for subscribed transfer
        TransferEvent event = receivedEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "Should receive event for subscribed transfer");
        assertEquals(subscribedTransferId, event.getTransferId());

        // Should not receive other events
        TransferEvent noEvent = receivedEvents.poll(1, TimeUnit.SECONDS);
        assertNull(noEvent, "Should not receive events for other transfers");
    }

    @Test
    @DisplayName("Multiple subscribers should all receive events")
    void multipleSubscribers_shouldAllReceiveEvents() throws Exception {
        // Arrange
        String transferId = "multi-subscriber-transfer";
        String topic = "/topic/transfer/" + transferId + "/progress";

        BlockingQueue<TransferEvent> queue1 = new LinkedBlockingQueue<>();
        BlockingQueue<TransferEvent> queue2 = new LinkedBlockingQueue<>();

        // Subscribe twice to same topic
        stompSession.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TransferEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue1.add((TransferEvent) payload);
            }
        });

        stompSession.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TransferEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue2.add((TransferEvent) payload);
            }
        });
        waitForSubscription();

        // Act
        publishAndWait(() -> eventBus.progress(transferId, 5000L, 10000L));

        // Assert - Both subscribers should receive the event
        TransferEvent event1 = queue1.poll(5, TimeUnit.SECONDS);
        assertNotNull(event1, "First subscriber should receive event");
        assertEquals(transferId, event1.getTransferId());

        TransferEvent event2 = queue2.poll(5, TimeUnit.SECONDS);
        assertNotNull(event2, "Second subscriber should receive event");
        assertEquals(transferId, event2.getTransferId());
    }
}
