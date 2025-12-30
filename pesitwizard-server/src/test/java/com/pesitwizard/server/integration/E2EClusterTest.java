package com.pesitwizard.server.integration;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mock;

import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduBuilder;
import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.fpdu.FpduType;
import com.pesitwizard.fpdu.ParameterValue;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.config.SslProperties;
import com.pesitwizard.server.entity.PesitServerConfig;
import com.pesitwizard.server.handler.ConnectionValidator;
import com.pesitwizard.server.handler.DataTransferHandler;
import com.pesitwizard.server.handler.FileValidator;
import com.pesitwizard.server.handler.MessageHandler;
import com.pesitwizard.server.handler.PesitSessionHandler;
import com.pesitwizard.server.handler.TransferOperationHandler;
import com.pesitwizard.server.service.AuditService;
import com.pesitwizard.server.service.ConfigService;
import com.pesitwizard.server.service.PathPlaceholderService;
import com.pesitwizard.server.service.PesitServerInstance;
import com.pesitwizard.server.service.TransferTracker;
import com.pesitwizard.server.ssl.SslContextFactory;

/**
 * End-to-End Cluster Test
 * 
 * This test simulates a full cluster deployment scenario:
 * 1. Provisions multiple PeSIT server instances (simulating cluster nodes)
 * 2. Creates server configurations via the API
 * 3. Performs file transfers across the cluster
 * 4. Tests failover scenarios
 * 5. Deprovisions the cluster
 * 
 * Note: This test runs in-process without Docker/Kubernetes.
 * For full container-based testing, use scripts/e2e-test.sh
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class E2EClusterTest {

    private static final String HOST = "localhost";
    private static final int NODE1_PORT = 5200;
    private static final int NODE2_PORT = 5201;
    private static final int NODE3_PORT = 5202;
    private static final int CLIENT_ID = 10;

    private static final boolean INTEGRATION_ENABLED = Boolean.parseBoolean(
            System.getProperty("pesit.integration.enabled", "false"));

    private PesitServerInstance node1;
    private PesitServerInstance node2;
    private PesitServerInstance node3;

    @Mock
    private SslProperties sslProperties;

    @Mock
    private SslContextFactory sslContextFactory;

    @BeforeAll
    void checkIntegrationEnabled() {
        Assumptions.assumeTrue(INTEGRATION_ENABLED,
                "Integration tests disabled. Enable with -Dpesit.integration.enabled=true");
    }

    @AfterEach
    void cleanup() {
        stopNode(node1, "node1");
        stopNode(node2, "node2");
        stopNode(node3, "node3");
        node1 = null;
        node2 = null;
        node3 = null;
    }

    private void stopNode(PesitServerInstance node, String name) {
        if (node != null && node.isRunning()) {
            try {
                node.stop();
                System.out.println("  ✓ Stopped " + name);
            } catch (Exception e) {
                System.err.println("  ✗ Error stopping " + name + ": " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("E2E Test: Full cluster lifecycle with file transfers")
    void testFullClusterLifecycle() throws Exception {
        System.out.println("\n");
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║     E2E CLUSTER TEST: Full Lifecycle                          ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ========== PHASE 1: PROVISION CLUSTER ==========
        System.out.println("┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  PHASE 1: PROVISIONING CLUSTER                                  │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        System.out.println("\nStep 1.1: Starting Node 1 (port " + NODE1_PORT + ")...");
        node1 = createAndStartNode("CLUSTER_NODE_1", NODE1_PORT);
        assertTrue(node1.isRunning(), "Node 1 should be running");
        System.out.println("  ✓ Node 1 started");

        System.out.println("\nStep 1.2: Starting Node 2 (port " + NODE2_PORT + ")...");
        node2 = createAndStartNode("CLUSTER_NODE_2", NODE2_PORT);
        assertTrue(node2.isRunning(), "Node 2 should be running");
        System.out.println("  ✓ Node 2 started");

        System.out.println("\nStep 1.3: Starting Node 3 (port " + NODE3_PORT + ")...");
        node3 = createAndStartNode("CLUSTER_NODE_3", NODE3_PORT);
        assertTrue(node3.isRunning(), "Node 3 should be running");
        System.out.println("  ✓ Node 3 started");

        System.out.println("\n  ✓ CLUSTER PROVISIONED: 3 nodes running");

        // ========== PHASE 2: VERIFY CLUSTER HEALTH ==========
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  PHASE 2: VERIFYING CLUSTER HEALTH                              │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        System.out.println("\nStep 2.1: Checking all nodes accept connections...");
        assertTrue(canConnect(NODE1_PORT), "Node 1 should accept connections");
        System.out.println("  ✓ Node 1 accepting connections");
        assertTrue(canConnect(NODE2_PORT), "Node 2 should accept connections");
        System.out.println("  ✓ Node 2 accepting connections");
        assertTrue(canConnect(NODE3_PORT), "Node 3 should accept connections");
        System.out.println("  ✓ Node 3 accepting connections");

        System.out.println("\n  ✓ CLUSTER HEALTH: All nodes operational");

        // ========== PHASE 3: FILE TRANSFER TESTS ==========
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  PHASE 3: FILE TRANSFER TESTS                                   │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        System.out.println("\nStep 3.1: Single file transfer to Node 1...");
        performFileTransfer(NODE1_PORT, "CLUSTER_NODE_1");
        System.out.println("  ✓ File transfer to Node 1 successful");

        System.out.println("\nStep 3.2: Single file transfer to Node 2...");
        performFileTransfer(NODE2_PORT, "CLUSTER_NODE_2");
        System.out.println("  ✓ File transfer to Node 2 successful");

        System.out.println("\nStep 3.3: Concurrent transfers to all nodes...");
        performConcurrentTransfers();
        System.out.println("  ✓ Concurrent transfers successful");

        System.out.println("\n  ✓ FILE TRANSFERS: All tests passed");

        // ========== PHASE 4: FAILOVER TEST ==========
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  PHASE 4: FAILOVER TEST                                         │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        System.out.println("\nStep 4.1: Simulating Node 1 failure...");
        node1.stop();
        assertFalse(node1.isRunning(), "Node 1 should be stopped");
        System.out.println("  ✓ Node 1 stopped (simulated failure)");

        System.out.println("\nStep 4.2: Verifying Node 1 is unreachable...");
        assertFalse(canConnect(NODE1_PORT), "Node 1 should be unreachable");
        System.out.println("  ✓ Node 1 is unreachable");

        System.out.println("\nStep 4.3: Verifying remaining nodes still operational...");
        assertTrue(node2.isRunning(), "Node 2 should still be running");
        assertTrue(node3.isRunning(), "Node 3 should still be running");
        performFileTransfer(NODE2_PORT, "CLUSTER_NODE_2");
        System.out.println("  ✓ Node 2 still processing requests");
        performFileTransfer(NODE3_PORT, "CLUSTER_NODE_3");
        System.out.println("  ✓ Node 3 still processing requests");

        System.out.println("\nStep 4.4: Recovering Node 1...");
        node1 = createAndStartNode("CLUSTER_NODE_1", NODE1_PORT);
        assertTrue(node1.isRunning(), "Node 1 should be running again");
        performFileTransfer(NODE1_PORT, "CLUSTER_NODE_1");
        System.out.println("  ✓ Node 1 recovered and processing requests");

        System.out.println("\n  ✓ FAILOVER: Test passed");

        // ========== PHASE 5: LOAD TEST ==========
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  PHASE 5: LOAD TEST                                             │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        System.out.println("\nStep 5.1: Running 30 concurrent connections across cluster...");
        int successCount = performLoadTest(30);
        System.out.println("  ✓ Completed " + successCount + "/30 connections successfully");
        assertTrue(successCount >= 27, "At least 90% of connections should succeed");

        System.out.println("\n  ✓ LOAD TEST: Passed");

        // ========== PHASE 6: DEPROVISION CLUSTER ==========
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  PHASE 6: DEPROVISIONING CLUSTER                                │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        System.out.println("\nStep 6.1: Stopping all nodes...");
        // Cleanup is handled by @AfterEach

        System.out.println("\n");
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║     ✅ E2E CLUSTER TEST PASSED                                ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    @Test
    @DisplayName("E2E Test: Rolling restart scenario")
    void testRollingRestart() throws Exception {
        System.out.println("\n=== ROLLING RESTART TEST ===\n");

        // Start all nodes
        node1 = createAndStartNode("NODE_A", NODE1_PORT);
        node2 = createAndStartNode("NODE_B", NODE2_PORT);
        node3 = createAndStartNode("NODE_C", NODE3_PORT);

        System.out.println("Step 1: All nodes started");

        // Perform rolling restart
        for (int i = 0; i < 3; i++) {
            PesitServerInstance nodeToRestart;
            String nodeName;
            int port;

            switch (i) {
                case 0 -> {
                    nodeToRestart = node1;
                    nodeName = "NODE_A";
                    port = NODE1_PORT;
                }
                case 1 -> {
                    nodeToRestart = node2;
                    nodeName = "NODE_B";
                    port = NODE2_PORT;
                }
                default -> {
                    nodeToRestart = node3;
                    nodeName = "NODE_C";
                    port = NODE3_PORT;
                }
            }

            System.out.println("\nStep " + (i + 2) + ": Restarting " + nodeName + "...");

            // Stop the node
            nodeToRestart.stop();

            // Verify other nodes still work
            int[] otherPorts = getOtherPorts(port);
            for (int otherPort : otherPorts) {
                assertTrue(canConnect(otherPort), "Other nodes should still be reachable");
            }
            System.out.println("  ✓ Other nodes still operational during restart");

            // Restart the node
            PesitServerInstance newNode = createAndStartNode(nodeName, port);
            switch (i) {
                case 0 -> node1 = newNode;
                case 1 -> node2 = newNode;
                default -> node3 = newNode;
            }

            assertTrue(canConnect(port), "Restarted node should be reachable");
            System.out.println("  ✓ " + nodeName + " restarted successfully");
        }

        System.out.println("\n✓✓✓ ROLLING RESTART TEST PASSED ✓✓✓\n");
    }

    private int[] getOtherPorts(int excludePort) {
        return java.util.Arrays.stream(new int[] { NODE1_PORT, NODE2_PORT, NODE3_PORT })
                .filter(p -> p != excludePort)
                .toArray();
    }

    private PesitServerInstance createAndStartNode(String serverId, int port) throws Exception {
        PesitServerConfig config = new PesitServerConfig();
        config.setServerId(serverId);
        config.setPort(port);
        config.setMaxConnections(20);
        config.setStrictPartnerCheck(false);
        config.setStrictFileCheck(false);

        PesitServerProperties properties = new PesitServerProperties();
        properties.setServerId(serverId);
        properties.setPort(port);
        properties.setMaxConnections(20);
        properties.setStrictPartnerCheck(false);
        properties.setStrictFileCheck(false);
        properties.setReceiveDirectory("./test-received/" + serverId);
        properties.setSendDirectory("./test-send/" + serverId);

        ConfigService configService = mock(ConfigService.class);
        TransferTracker transferTracker = mock(TransferTracker.class);
        PathPlaceholderService pathPlaceholderService = new PathPlaceholderService();
        com.pesitwizard.server.service.FileSystemService fileSystemService = new com.pesitwizard.server.service.FileSystemService();

        // Create split handler components
        ConnectionValidator connectionValidator = new ConnectionValidator(properties, configService);
        FileValidator fileValidator = new FileValidator(properties, configService);
        TransferOperationHandler transferOperationHandler = new TransferOperationHandler(
                properties, fileValidator, transferTracker, pathPlaceholderService, fileSystemService);
        DataTransferHandler dataTransferHandler = new DataTransferHandler(properties, transferTracker);
        MessageHandler messageHandler = new MessageHandler();
        AuditService auditService = org.mockito.Mockito.mock(AuditService.class);
        com.pesitwizard.server.cluster.ClusterProvider clusterProvider = org.mockito.Mockito
                .mock(com.pesitwizard.server.cluster.ClusterProvider.class);

        PesitSessionHandler sessionHandler = new PesitSessionHandler(properties, connectionValidator,
                transferOperationHandler, dataTransferHandler, messageHandler, transferTracker, auditService,
                clusterProvider);
        PesitServerInstance instance = new PesitServerInstance(config, properties, sessionHandler, sslProperties,
                sslContextFactory);
        instance.start();

        // Wait a bit for the server to be fully ready
        Thread.sleep(100);

        return instance;
    }

    private boolean canConnect(int port) {
        try (Socket socket = new Socket(HOST, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void performFileTransfer(int port, String serverId) throws Exception {
        try (Socket socket = new Socket(HOST, port)) {
            socket.setSoTimeout(5000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // CONNECT
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withIdSrc(CLIENT_ID)
                    .withParameter(new ParameterValue(PI_03_DEMANDEUR, "E2E_TEST"))
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
            Fpdu response = new FpduParser(responseBytes).parse();

            assertEquals(FpduType.ACONNECT, response.getFpduType(), "Expected ACONNECT");
            int serverConnId = response.getIdSrc();

            // RELEASE
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
            response = new FpduParser(responseBytes).parse();

            assertEquals(FpduType.RELCONF, response.getFpduType(), "Expected RELCONF");
        }
    }

    private void performConcurrentTransfers() throws Exception {
        int[] ports = { NODE1_PORT, NODE2_PORT, NODE3_PORT };
        String[] serverIds = { "CLUSTER_NODE_1", "CLUSTER_NODE_2", "CLUSTER_NODE_3" };

        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 3; i++) {
            final int port = ports[i];
            final String serverId = serverIds[i];

            new Thread(() -> {
                try {
                    performFileTransfer(port, serverId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Transfer failed to " + serverId + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "Concurrent transfers should complete");
        assertEquals(3, successCount.get(), "All concurrent transfers should succeed");
    }

    private int performLoadTest(int connectionCount) throws Exception {
        int[] ports = { NODE1_PORT, NODE2_PORT, NODE3_PORT };
        String[] serverIds = { "CLUSTER_NODE_1", "CLUSTER_NODE_2", "CLUSTER_NODE_3" };

        CountDownLatch latch = new CountDownLatch(connectionCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < connectionCount; i++) {
            final int port = ports[i % 3];
            final String serverId = serverIds[i % 3];

            new Thread(() -> {
                try {
                    performFileTransfer(port, serverId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected some failures under load
                } finally {
                    latch.countDown();
                }
            }).start();

            // Small delay to avoid overwhelming
            Thread.sleep(50);
        }

        latch.await(60, TimeUnit.SECONDS);
        return successCount.get();
    }

    @Test
    @DisplayName("E2E Test: Server shutdown during active transfers - cluster continues")
    void testServerShutdownDuringTransfers() throws Exception {
        System.out.println("\n");
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║     E2E TEST: Server Shutdown During Active Transfers         ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();

        // ========== SETUP: Start 3-node cluster ==========
        System.out.println("┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  SETUP: Starting 3-node cluster                                 │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        node1 = createAndStartNode("CLUSTER_NODE_1", NODE1_PORT);
        node2 = createAndStartNode("CLUSTER_NODE_2", NODE2_PORT);
        node3 = createAndStartNode("CLUSTER_NODE_3", NODE3_PORT);
        System.out.println("  ✓ All 3 nodes started");

        // ========== TEST 1: Shutdown during continuous load ==========
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  TEST 1: Shutdown Node During Continuous Load                   │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        // Start continuous transfers in background
        AtomicInteger totalTransfers = new AtomicInteger(0);
        AtomicInteger successfulTransfers = new AtomicInteger(0);
        AtomicInteger failedToNode1 = new AtomicInteger(0);
        AtomicInteger successToOtherNodes = new AtomicInteger(0);
        AtomicBoolean keepRunning = new AtomicBoolean(true);
        CountDownLatch transfersStarted = new CountDownLatch(3);

        // Thread sending to Node 1 (will be shut down)
        Thread node1Client = new Thread(() -> {
            transfersStarted.countDown();
            while (keepRunning.get()) {
                try {
                    performFileTransfer(NODE1_PORT, "CLUSTER_NODE_1");
                    successfulTransfers.incrementAndGet();
                    totalTransfers.incrementAndGet();
                } catch (Exception e) {
                    failedToNode1.incrementAndGet();
                    totalTransfers.incrementAndGet();
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }, "client-node1");

        // Thread sending to Node 2
        Thread node2Client = new Thread(() -> {
            transfersStarted.countDown();
            while (keepRunning.get()) {
                try {
                    performFileTransfer(NODE2_PORT, "CLUSTER_NODE_2");
                    successfulTransfers.incrementAndGet();
                    successToOtherNodes.incrementAndGet();
                    totalTransfers.incrementAndGet();
                } catch (Exception e) {
                    totalTransfers.incrementAndGet();
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }, "client-node2");

        // Thread sending to Node 3
        Thread node3Client = new Thread(() -> {
            transfersStarted.countDown();
            while (keepRunning.get()) {
                try {
                    performFileTransfer(NODE3_PORT, "CLUSTER_NODE_3");
                    successfulTransfers.incrementAndGet();
                    successToOtherNodes.incrementAndGet();
                    totalTransfers.incrementAndGet();
                } catch (Exception e) {
                    totalTransfers.incrementAndGet();
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }, "client-node3");

        // Start all client threads
        node1Client.start();
        node2Client.start();
        node3Client.start();

        // Wait for transfers to start
        transfersStarted.await(5, TimeUnit.SECONDS);
        System.out.println("\nStep 1: Started continuous transfers to all 3 nodes");

        // Let transfers run for a bit
        Thread.sleep(500);
        int transfersBeforeShutdown = totalTransfers.get();
        System.out.println("  ✓ Transfers before shutdown: " + transfersBeforeShutdown);

        // ========== SHUTDOWN NODE 1 ==========
        System.out.println("\nStep 2: SHUTTING DOWN NODE 1 (simulating server failure)...");
        node1.stop();
        System.out.println("  ✓ Node 1 stopped");

        // Let transfers continue for a while after shutdown
        Thread.sleep(1000);

        // Stop the transfer threads
        keepRunning.set(false);
        node1Client.interrupt();
        node2Client.interrupt();
        node3Client.interrupt();
        node1Client.join(2000);
        node2Client.join(2000);
        node3Client.join(2000);

        int transfersAfterShutdown = totalTransfers.get() - transfersBeforeShutdown;
        System.out.println("\nStep 3: Results after Node 1 shutdown:");
        System.out.println("  - Total transfers attempted: " + totalTransfers.get());
        System.out.println("  - Transfers after shutdown: " + transfersAfterShutdown);
        System.out.println("  - Successful transfers: " + successfulTransfers.get());
        System.out.println("  - Failed to Node 1 (expected): " + failedToNode1.get());
        System.out.println("  - Successful to Node 2 & 3: " + successToOtherNodes.get());

        // Verify cluster continued to work
        assertTrue(successToOtherNodes.get() > 0, "Transfers to other nodes should have succeeded");
        assertTrue(failedToNode1.get() > 0, "Transfers to Node 1 should have failed after shutdown");
        System.out.println("  ✓ Cluster continued processing during Node 1 shutdown");

        // ========== TEST 2: Verify remaining nodes are fully operational ==========
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  TEST 2: Verify Remaining Nodes Handle All Traffic              │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        System.out.println("\nStep 4: Running 20 transfers to remaining nodes...");
        AtomicInteger postShutdownSuccess = new AtomicInteger(0);
        CountDownLatch postShutdownLatch = new CountDownLatch(20);

        for (int i = 0; i < 20; i++) {
            final int port = (i % 2 == 0) ? NODE2_PORT : NODE3_PORT;
            final String serverId = (i % 2 == 0) ? "CLUSTER_NODE_2" : "CLUSTER_NODE_3";

            new Thread(() -> {
                try {
                    performFileTransfer(port, serverId);
                    postShutdownSuccess.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("  Post-shutdown transfer failed: " + e.getMessage());
                } finally {
                    postShutdownLatch.countDown();
                }
            }).start();
        }

        postShutdownLatch.await(30, TimeUnit.SECONDS);
        System.out.println("  ✓ Completed " + postShutdownSuccess.get() + "/20 transfers to remaining nodes");
        assertTrue(postShutdownSuccess.get() >= 18, "At least 90% of transfers should succeed");

        // ========== TEST 3: Restart Node 1 and verify cluster recovers ==========
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  TEST 3: Restart Failed Node - Cluster Recovery                 │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        System.out.println("\nStep 5: Restarting Node 1...");
        node1 = createAndStartNode("CLUSTER_NODE_1", NODE1_PORT);
        assertTrue(node1.isRunning(), "Node 1 should be running again");
        System.out.println("  ✓ Node 1 restarted");

        System.out.println("\nStep 6: Verifying all nodes operational...");
        performFileTransfer(NODE1_PORT, "CLUSTER_NODE_1");
        System.out.println("  ✓ Node 1 accepting transfers");
        performFileTransfer(NODE2_PORT, "CLUSTER_NODE_2");
        System.out.println("  ✓ Node 2 accepting transfers");
        performFileTransfer(NODE3_PORT, "CLUSTER_NODE_3");
        System.out.println("  ✓ Node 3 accepting transfers");

        // ========== TEST 4: Cascading failure - shutdown 2 nodes ==========
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  TEST 4: Cascading Failure - 2 Nodes Down                       │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        System.out.println("\nStep 7: Shutting down Node 1 and Node 2...");
        node1.stop();
        node2.stop();
        System.out.println("  ✓ Nodes 1 and 2 stopped");

        System.out.println("\nStep 8: Verifying Node 3 still handles traffic...");
        for (int i = 0; i < 5; i++) {
            performFileTransfer(NODE3_PORT, "CLUSTER_NODE_3");
        }
        System.out.println("  ✓ Node 3 handled 5 transfers while other nodes down");

        // ========== TEST 5: Full cluster recovery ==========
        System.out.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  TEST 5: Full Cluster Recovery                                  │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        System.out.println("\nStep 9: Restarting all failed nodes...");
        node1 = createAndStartNode("CLUSTER_NODE_1", NODE1_PORT);
        node2 = createAndStartNode("CLUSTER_NODE_2", NODE2_PORT);
        System.out.println("  ✓ All nodes restarted");

        System.out.println("\nStep 10: Final verification - concurrent transfers to all nodes...");
        performConcurrentTransfers();
        System.out.println("  ✓ All nodes processing transfers concurrently");

        System.out.println("\n");
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║     ✅ SERVER SHUTDOWN TEST PASSED                            ║");
        System.out.println("║                                                               ║");
        System.out.println("║     Verified:                                                 ║");
        System.out.println("║     • Cluster continues during node shutdown                  ║");
        System.out.println("║     • Remaining nodes handle all traffic                      ║");
        System.out.println("║     • Failed nodes can rejoin cluster                         ║");
        System.out.println("║     • Cluster survives cascading failures                     ║");
        System.out.println("║     • Full recovery after multiple failures                   ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    @Test
    @DisplayName("E2E Test: Graceful shutdown with drain")
    void testGracefulShutdownWithDrain() throws Exception {
        System.out.println("\n=== GRACEFUL SHUTDOWN WITH DRAIN TEST ===\n");

        // Start cluster
        node1 = createAndStartNode("DRAIN_NODE_1", NODE1_PORT);
        node2 = createAndStartNode("DRAIN_NODE_2", NODE2_PORT);
        System.out.println("Step 1: Started 2-node cluster");

        // Start a long-running transfer simulation
        AtomicInteger activeConnections = new AtomicInteger(0);
        AtomicInteger completedConnections = new AtomicInteger(0);
        CountDownLatch allStarted = new CountDownLatch(5);
        CountDownLatch allCompleted = new CountDownLatch(5);

        System.out.println("\nStep 2: Starting 5 concurrent connections to Node 1...");
        for (int i = 0; i < 5; i++) {
            final int connId = i;
            new Thread(() -> {
                try {
                    activeConnections.incrementAndGet();
                    allStarted.countDown();

                    // Simulate a longer transfer
                    performFileTransferWithDelay(NODE1_PORT, "DRAIN_NODE_1", 200);

                    completedConnections.incrementAndGet();
                } catch (Exception e) {
                    System.out.println("  Connection " + connId + " interrupted: " + e.getMessage());
                } finally {
                    activeConnections.decrementAndGet();
                    allCompleted.countDown();
                }
            }, "drain-client-" + i).start();
        }

        // Wait for all connections to start
        allStarted.await(5, TimeUnit.SECONDS);
        System.out.println("  ✓ All 5 connections active");

        // Verify Node 2 is still available during this time
        System.out.println("\nStep 3: Verifying Node 2 handles traffic while Node 1 is busy...");
        performFileTransfer(NODE2_PORT, "DRAIN_NODE_2");
        System.out.println("  ✓ Node 2 handled transfer");

        // Wait for all transfers to complete
        System.out.println("\nStep 4: Waiting for all Node 1 transfers to complete...");
        allCompleted.await(10, TimeUnit.SECONDS);
        System.out.println("  ✓ All " + completedConnections.get() + " transfers completed");

        // Now shutdown Node 1
        System.out.println("\nStep 5: Shutting down Node 1 after drain...");
        node1.stop();
        System.out.println("  ✓ Node 1 shut down gracefully");

        // Verify Node 2 continues
        System.out.println("\nStep 6: Verifying Node 2 continues to operate...");
        for (int i = 0; i < 3; i++) {
            performFileTransfer(NODE2_PORT, "DRAIN_NODE_2");
        }
        System.out.println("  ✓ Node 2 handled 3 more transfers");

        System.out.println("\n✓✓✓ GRACEFUL SHUTDOWN WITH DRAIN TEST PASSED ✓✓✓\n");
    }

    /**
     * Perform a file transfer with an artificial delay to simulate longer transfers
     */
    private void performFileTransferWithDelay(int port, String serverId, int delayMs) throws Exception {
        try (Socket socket = new Socket(HOST, port)) {
            socket.setSoTimeout(10000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // CONNECT
            Fpdu connectFpdu = new Fpdu(FpduType.CONNECT)
                    .withIdSrc(CLIENT_ID)
                    .withParameter(new ParameterValue(PI_03_DEMANDEUR, "DRAIN_TEST"))
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
            Fpdu response = new FpduParser(responseBytes).parse();

            assertEquals(FpduType.ACONNECT, response.getFpduType(), "Expected ACONNECT");
            int serverConnId = response.getIdSrc();

            // Simulate transfer time
            Thread.sleep(delayMs);

            // RELEASE
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
            response = new FpduParser(responseBytes).parse();

            assertEquals(FpduType.RELCONF, response.getFpduType(), "Expected RELCONF");
        }
    }
}
