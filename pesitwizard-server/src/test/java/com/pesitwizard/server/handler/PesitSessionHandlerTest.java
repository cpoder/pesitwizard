package com.pesitwizard.server.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.service.TransferTracker;
import com.pesitwizard.server.state.ServerState;

@ExtendWith(MockitoExtension.class)
@DisplayName("PesitSessionHandler Tests")
class PesitSessionHandlerTest {

    @Mock
    private PesitServerProperties properties;

    @Mock
    private ConnectionValidator connectionValidator;

    @Mock
    private TransferOperationHandler transferOperationHandler;

    @Mock
    private DataTransferHandler dataTransferHandler;

    @Mock
    private MessageHandler messageHandler;

    @Mock
    private TransferTracker transferTracker;

    private PesitSessionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PesitSessionHandler(properties, connectionValidator,
                transferOperationHandler, dataTransferHandler, messageHandler, transferTracker);
        lenient().when(properties.getServerId()).thenReturn("TEST_SERVER");
    }

    @Test
    @DisplayName("createSession should create new session with remote address")
    void createSessionShouldCreateNewSession() {
        SessionContext ctx = handler.createSession("192.168.1.100");

        assertNotNull(ctx);
        assertNotNull(ctx.getSessionId());
        assertEquals("192.168.1.100", ctx.getRemoteAddress());
        assertEquals("TEST_SERVER", ctx.getOurServerId());
        assertEquals(ServerState.CN01_REPOS, ctx.getState());
    }

    @Test
    @DisplayName("createSession with serverId should use provided serverId")
    void createSessionWithServerIdShouldUseProvidedId() {
        SessionContext ctx = handler.createSession("192.168.1.100", "CUSTOM_SERVER");

        assertNotNull(ctx);
        assertEquals("CUSTOM_SERVER", ctx.getOurServerId());
    }

    @Test
    @DisplayName("createSession should generate unique connection ID")
    void createSessionShouldGenerateConnectionId() {
        SessionContext ctx = handler.createSession("192.168.1.100");

        assertTrue(ctx.getServerConnectionId() > 0);
        assertTrue(ctx.getServerConnectionId() <= 256);
    }

    @Test
    @DisplayName("createSession should generate unique session IDs")
    void createSessionShouldGenerateUniqueSessionIds() {
        SessionContext ctx1 = handler.createSession("192.168.1.100");
        SessionContext ctx2 = handler.createSession("192.168.1.101");

        assertNotEquals(ctx1.getSessionId(), ctx2.getSessionId());
    }
}
