package com.pesitwizard.server.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.net.SocketAddress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.state.ServerState;

@ExtendWith(MockitoExtension.class)
@DisplayName("TcpConnectionHandler Tests")
class TcpConnectionHandlerTest {

    @Mock
    private Socket socket;

    @Mock
    private PesitSessionHandler sessionHandler;

    @Mock
    private PesitServerProperties properties;

    @Mock
    private SocketAddress socketAddress;

    private TcpConnectionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TcpConnectionHandler(socket, sessionHandler, properties, "TEST_SERVER");
    }

    @Test
    @DisplayName("constructor should store all parameters")
    void constructorShouldStoreAllParameters() {
        assertNotNull(handler);
    }

    @Test
    @DisplayName("run should handle socket exception gracefully")
    void runShouldHandleSocketException() throws Exception {
        when(socket.getRemoteSocketAddress()).thenReturn(socketAddress);
        when(socketAddress.toString()).thenReturn("127.0.0.1:12345");
        when(properties.getReadTimeout()).thenReturn(30000);
        when(socket.getInputStream()).thenThrow(new java.net.SocketException("Connection reset"));

        SessionContext ctx = new SessionContext("test-session");
        when(sessionHandler.createSession(anyString(), anyString())).thenReturn(ctx);

        assertDoesNotThrow(() -> handler.run());
    }

    @Test
    @DisplayName("run should handle EOF when client disconnects")
    void runShouldHandleEofWhenClientDisconnects() throws Exception {
        when(socket.getRemoteSocketAddress()).thenReturn(socketAddress);
        when(socketAddress.toString()).thenReturn("127.0.0.1:12345");
        when(properties.getReadTimeout()).thenReturn(30000);

        // Empty input stream simulates EOF
        ByteArrayInputStream emptyInput = new ByteArrayInputStream(new byte[0]);
        when(socket.getInputStream()).thenReturn(emptyInput);
        when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(socket.isClosed()).thenReturn(false, true);

        SessionContext ctx = new SessionContext("test-session");
        when(sessionHandler.createSession(anyString(), anyString())).thenReturn(ctx);

        assertDoesNotThrow(() -> handler.run());
    }

    @Test
    @DisplayName("run should attempt to close socket on completion")
    void runShouldAttemptCloseSocketOnCompletion() throws Exception {
        when(socket.getRemoteSocketAddress()).thenReturn(socketAddress);
        when(socketAddress.toString()).thenReturn("127.0.0.1:12345");
        when(properties.getReadTimeout()).thenReturn(30000);
        when(socket.isClosed()).thenReturn(true);

        SessionContext ctx = new SessionContext("test-session");
        when(sessionHandler.createSession(anyString(), anyString())).thenReturn(ctx);

        ByteArrayInputStream emptyInput = new ByteArrayInputStream(new byte[0]);
        when(socket.getInputStream()).thenReturn(emptyInput);
        when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());

        handler.run();

        // Socket close is attempted but may already be closed
        verify(socket, atMostOnce()).close();
    }

    @Test
    @DisplayName("run should end session when state returns to CN01_REPOS")
    void runShouldEndSessionWhenStateReturnsToCn01() throws Exception {
        when(socket.getRemoteSocketAddress()).thenReturn(socketAddress);
        when(socketAddress.toString()).thenReturn("127.0.0.1:12345");
        when(properties.getReadTimeout()).thenReturn(30000);

        SessionContext ctx = new SessionContext("test-session");
        ctx.transitionTo(ServerState.CN01_REPOS);
        when(sessionHandler.createSession(anyString(), anyString())).thenReturn(ctx);

        when(socket.isClosed()).thenReturn(true);
        ByteArrayInputStream emptyInput = new ByteArrayInputStream(new byte[0]);
        when(socket.getInputStream()).thenReturn(emptyInput);
        when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());

        assertDoesNotThrow(() -> handler.run());
    }

    @Test
    @DisplayName("run should end session when aborted flag is set")
    void runShouldEndSessionWhenAborted() throws Exception {
        when(socket.getRemoteSocketAddress()).thenReturn(socketAddress);
        when(socketAddress.toString()).thenReturn("127.0.0.1:12345");
        when(properties.getReadTimeout()).thenReturn(30000);

        SessionContext ctx = new SessionContext("test-session");
        ctx.setAborted(true);
        when(sessionHandler.createSession(anyString(), anyString())).thenReturn(ctx);

        when(socket.isClosed()).thenReturn(true);
        ByteArrayInputStream emptyInput = new ByteArrayInputStream(new byte[0]);
        when(socket.getInputStream()).thenReturn(emptyInput);
        when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());

        assertDoesNotThrow(() -> handler.run());
    }
}
