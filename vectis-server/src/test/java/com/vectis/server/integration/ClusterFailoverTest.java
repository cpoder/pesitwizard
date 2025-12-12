package com.vectis.server.integration;

import static com.vectis.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ConnectException;
import java.net.Socket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.vectis.fpdu.Fpdu;
import com.vectis.fpdu.FpduBuilder;
import com.vectis.fpdu.FpduParser;
import com.vectis.fpdu.FpduType;
import com.vectis.fpdu.ParameterValue;
import com.vectis.server.config.PesitServerProperties;
import com.vectis.server.entity.PesitServerConfig;
import com.vectis.server.handler.PesitSessionHandler;
import com.vectis.server.service.ConfigService;
import com.vectis.server.service.PathPlaceholderService;
import com.vectis.server.service.PesitServerInstance;
import com.vectis.server.service.TransferTracker;

/**
 * Integration test for cluster failover scenarios.
 * Tests that when one PeSIT server instance goes down,
 * another instance can take over and process requests.
 * 
 * This test simulates a multi-node scenario on a single machine
 * by running multiple PesitServerInstance objects.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ClusterFailoverTest {

    private static final String HOST = "localhost";
    private static final int PRIMARY_PORT = 5100;
    private static final int SECONDARY_PORT = 5101;
    private static final String SERVER_ID = "FAILOVER_TEST_SERVER";
    private static final int CLIENT_ID = 5;

    private static final boolean INTEGRATION_ENABLED = Boolean.parseBoolean(
            System.getProperty("pesit.integration.enabled", "false"));

    private PesitServerInstance primaryServer;
    private PesitServerInstance secondaryServer;

    @BeforeAll
    void checkIntegrationEnabled() {
        Assumptions.assumeTrue(INTEGRATION_ENABLED,
                "Integration tests disabled. Enable with -Dpesit.integration.enabled=true");
    }

    @AfterEach
    void cleanup() {
        if (primaryServer != null && primaryServer.isRunning()) {
            primaryServer.stop();
        }
        if (secondaryServer != null && secondaryServer.isRunning()) {
            secondaryServer.stop();
        }
    }

    @Test
    @DisplayName("Test failover: secondary server takes over when primary goes down")
    void testFailoverToSecondaryServer() throws Exception {
        System.out.println("\n=== CLUSTER FAILOVER TEST ===\n");

        // Step 1: Start primary server
        System.out.println("Step 1: Starting PRIMARY server on port " + PRIMARY_PORT);
        primaryServer = createServerInstance(SERVER_ID, PRIMARY_PORT);
        primaryServer.start();
        assertTrue(primaryServer.isRunning(), "Primary server should be running");
        System.out.println("  ✓ Primary server started");

        // Step 2: Verify primary server accepts connections
        System.out.println("\nStep 2: Verify PRIMARY server accepts connections");
        assertTrue(canConnectAndAuthenticate(PRIMARY_PORT, SERVER_ID),
                "Should be able to connect to primary server");
        System.out.println("  ✓ Primary server accepting connections");

        // Step 3: Start secondary server (simulating standby node)
        System.out.println("\nStep 3: Starting SECONDARY server on port " + SECONDARY_PORT);
        secondaryServer = createServerInstance(SERVER_ID, SECONDARY_PORT);
        secondaryServer.start();
        assertTrue(secondaryServer.isRunning(), "Secondary server should be running");
        System.out.println("  ✓ Secondary server started (standby)");

        // Step 4: Verify secondary server also accepts connections
        System.out.println("\nStep 4: Verify SECONDARY server accepts connections");
        assertTrue(canConnectAndAuthenticate(SECONDARY_PORT, SERVER_ID),
                "Should be able to connect to secondary server");
        System.out.println("  ✓ Secondary server accepting connections");

        // Step 5: Simulate primary server failure
        System.out.println("\nStep 5: Simulating PRIMARY server failure...");
        primaryServer.stop();
        assertFalse(primaryServer.isRunning(), "Primary server should be stopped");
        System.out.println("  ✓ Primary server stopped (simulated failure)");

        // Step 6: Verify primary is no longer reachable
        System.out.println("\nStep 6: Verify PRIMARY server is unreachable");
        assertFalse(canConnect(PRIMARY_PORT), "Primary server should be unreachable");
        System.out.println("  ✓ Primary server is unreachable (as expected)");

        // Step 7: Verify secondary server is still processing requests
        System.out.println("\nStep 7: Verify SECONDARY server is still processing requests");
        assertTrue(secondaryServer.isRunning(), "Secondary server should still be running");
        assertTrue(canConnectAndAuthenticate(SECONDARY_PORT, SERVER_ID),
                "Secondary server should still accept connections");
        System.out.println("  ✓ Secondary server is processing requests (FAILOVER SUCCESS)");

        // Step 8: Perform a full connection cycle on secondary
        System.out.println("\nStep 8: Perform full connection cycle on SECONDARY server");
        performFullConnectionCycle(SECONDARY_PORT, SERVER_ID);
        System.out.println("  ✓ Full connection cycle completed on secondary server");

        System.out.println("\n✓✓✓ CLUSTER FAILOVER TEST PASSED ✓✓✓\n");
    }

    @Test
    @DisplayName("Test load balancing: both servers can handle requests simultaneously")
    void testBothServersHandleRequests() throws Exception {
        System.out.println("\n=== LOAD BALANCING TEST ===\n");

        // Start both servers
        System.out.println("Step 1: Starting both servers");
        primaryServer = createServerInstance("SERVER_A", PRIMARY_PORT);
        secondaryServer = createServerInstance("SERVER_B", SECONDARY_PORT);

        primaryServer.start();
        secondaryServer.start();

        assertTrue(primaryServer.isRunning(), "Server A should be running");
        assertTrue(secondaryServer.isRunning(), "Server B should be running");
        System.out.println("  ✓ Both servers started");

        // Connect to both servers simultaneously
        System.out.println("\nStep 2: Connect to both servers simultaneously");

        Thread clientA = new Thread(() -> {
            try {
                for (int i = 0; i < 3; i++) {
                    assertTrue(canConnectAndAuthenticate(PRIMARY_PORT, "SERVER_A"));
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                fail("Client A failed: " + e.getMessage());
            }
        }, "client-A");

        Thread clientB = new Thread(() -> {
            try {
                for (int i = 0; i < 3; i++) {
                    assertTrue(canConnectAndAuthenticate(SECONDARY_PORT, "SERVER_B"));
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                fail("Client B failed: " + e.getMessage());
            }
        }, "client-B");

        clientA.start();
        clientB.start();

        clientA.join(5000);
        clientB.join(5000);

        assertFalse(clientA.isAlive(), "Client A should have finished");
        assertFalse(clientB.isAlive(), "Client B should have finished");

        System.out.println("  ✓ Both servers handled requests simultaneously");

        System.out.println("\n✓✓✓ LOAD BALANCING TEST PASSED ✓✓✓\n");
    }

    @Test
    @DisplayName("Test server restart: server can restart after failure")
    void testServerRestart() throws Exception {
        System.out.println("\n=== SERVER RESTART TEST ===\n");

        // Start server
        System.out.println("Step 1: Starting server");
        primaryServer = createServerInstance(SERVER_ID, PRIMARY_PORT);
        primaryServer.start();
        assertTrue(primaryServer.isRunning());
        System.out.println("  ✓ Server started");

        // Verify it works
        System.out.println("\nStep 2: Verify server works");
        assertTrue(canConnectAndAuthenticate(PRIMARY_PORT, SERVER_ID));
        System.out.println("  ✓ Server accepting connections");

        // Stop server (simulate crash)
        System.out.println("\nStep 3: Stop server (simulate crash)");
        primaryServer.stop();
        assertFalse(primaryServer.isRunning());
        System.out.println("  ✓ Server stopped");

        // Wait a moment
        Thread.sleep(500);

        // Restart server
        System.out.println("\nStep 4: Restart server");
        primaryServer = createServerInstance(SERVER_ID, PRIMARY_PORT);
        primaryServer.start();
        assertTrue(primaryServer.isRunning());
        System.out.println("  ✓ Server restarted");

        // Verify it works again
        System.out.println("\nStep 5: Verify server works after restart");
        assertTrue(canConnectAndAuthenticate(PRIMARY_PORT, SERVER_ID));
        System.out.println("  ✓ Server accepting connections after restart");

        System.out.println("\n✓✓✓ SERVER RESTART TEST PASSED ✓✓✓\n");
    }

    /**
     * Create a PeSIT server instance for testing
     */
    private PesitServerInstance createServerInstance(String serverId, int port) {
        PesitServerConfig config = new PesitServerConfig();
        config.setServerId(serverId);
        config.setPort(port);
        config.setMaxConnections(10);
        config.setStrictPartnerCheck(false);
        config.setStrictFileCheck(false);

        PesitServerProperties properties = new PesitServerProperties();
        properties.setServerId(serverId);
        properties.setPort(port);
        properties.setMaxConnections(10);
        properties.setStrictPartnerCheck(false);
        properties.setStrictFileCheck(false);
        properties.setReceiveDirectory("./test-received");
        properties.setSendDirectory("./test-send");

        ConfigService configService = mock(ConfigService.class);
        TransferTracker transferTracker = mock(TransferTracker.class);
        PathPlaceholderService pathPlaceholderService = new PathPlaceholderService();
        PesitSessionHandler sessionHandler = new PesitSessionHandler(properties, configService, transferTracker,
                pathPlaceholderService);

        return new PesitServerInstance(config, properties, sessionHandler);
    }

    /**
     * Check if we can connect to a port
     */
    private boolean canConnect(int port) {
        try (Socket socket = new Socket(HOST, port)) {
            return true;
        } catch (ConnectException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if we can connect and authenticate with a server
     */
    private boolean canConnectAndAuthenticate(int port, String serverId) {
        try (Socket socket = new Socket(HOST, port)) {
            socket.setSoTimeout(5000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Send CONNECT
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withIdSrc(CLIENT_ID)
                    .withParameter(new ParameterValue(PI_03_DEMANDEUR, "TEST"))
                    .withParameter(new ParameterValue(PI_04_SERVEUR, serverId))
                    .withParameter(new ParameterValue(PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(PI_22_TYPE_ACCES, 0));

            byte[] fpduBytes = FpduBuilder.buildFpdu(connectFpdu);
            out.writeShort(fpduBytes.length);
            out.write(fpduBytes);
            out.flush();

            // Read response
            int len = in.readUnsignedShort();
            byte[] responseBytes = new byte[len];
            in.readFully(responseBytes);
            Fpdu response = new FpduParser(responseBytes).parse();

            // Should get ACONNECT (accepted) since strict checks are disabled
            return response.getFpduType() == FpduType.ACONNECT;

        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Perform a full connection cycle: CONNECT -> RELEASE
     */
    private void performFullConnectionCycle(int port, String serverId) throws Exception {
        try (Socket socket = new Socket(HOST, port)) {
            socket.setSoTimeout(5000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // CONNECT
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withIdSrc(CLIENT_ID)
                    .withParameter(new ParameterValue(PI_03_DEMANDEUR, "TEST"))
                    .withParameter(new ParameterValue(PI_04_SERVEUR, serverId))
                    .withParameter(new ParameterValue(PI_06_VERSION, 2))
                    .withParameter(new ParameterValue(PI_22_TYPE_ACCES, 0));

            byte[] fpduBytes = FpduBuilder.buildFpdu(connectFpdu);
            out.writeShort(fpduBytes.length);
            out.write(fpduBytes);
            out.flush();

            int len = in.readUnsignedShort();
            byte[] responseBytes = new byte[len];
            in.readFully(responseBytes);
            Fpdu aconnect = new FpduParser(responseBytes).parse();

            assertEquals(FpduType.ACONNECT, aconnect.getFpduType(), "Expected ACONNECT");
            int serverConnId = aconnect.getIdSrc();

            // RELEASE (PI_02_DIAG is mandatory - use 0x000000 for success)
            Fpdu releaseFpdu = new Fpdu(FpduType.RELEASE)
                    .withIdDst(serverConnId)
                    .withIdSrc(0)
                    .withParameter(new ParameterValue(PI_02_DIAG, new byte[] { 0x00, 0x00, 0x00 }));

            fpduBytes = FpduBuilder.buildFpdu(releaseFpdu);
            out.writeShort(fpduBytes.length);
            out.write(fpduBytes);
            out.flush();

            len = in.readUnsignedShort();
            responseBytes = new byte[len];
            in.readFully(responseBytes);
            Fpdu relconf = new FpduParser(responseBytes).parse();

            assertEquals(FpduType.RELCONF, relconf.getFpduType(), "Expected RELCONF");
        }
    }
}
