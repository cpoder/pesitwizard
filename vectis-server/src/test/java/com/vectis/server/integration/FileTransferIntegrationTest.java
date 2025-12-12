package com.vectis.server.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import com.vectis.client.config.ClientConfig;
import com.vectis.client.service.PesitClientService;
import com.vectis.client.service.PesitClientService.TransferResult;
import com.vectis.server.config.PesitServerProperties;
import com.vectis.server.entity.Partner;
import com.vectis.server.entity.PesitServerConfig;
import com.vectis.server.entity.VirtualFile;
import com.vectis.server.service.ConfigService;
import com.vectis.server.service.PesitServerManager;

/**
 * Integration test for file transfer between PeSIT client and server.
 * 
 * Uses Spring Boot Test with mocked ConfigService returning hardcoded
 * partner and virtual file configurations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FileTransferIntegrationTest {

    private static final String HOST = "localhost";
    private static final String CLIENT_ID = "TEST_CLIENT";

    // Static paths for test directories - set before Spring context starts
    private static Path staticSendDirectory;
    private static Path staticReceiveDirectory;

    static {
        try {
            Path tempDir = Files.createTempDirectory("pesit-integration-");
            staticSendDirectory = tempDir.resolve("send");
            staticReceiveDirectory = tempDir.resolve("receive");
            Files.createDirectories(staticSendDirectory);
            Files.createDirectories(staticReceiveDirectory);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test directories", e);
        }
    }

    @TestConfiguration
    static class MockConfigServiceConfiguration {

        @Bean
        @Primary
        public ConfigService configService() {
            ConfigService mock = Mockito.mock(ConfigService.class);

            // Mock partner - allow any partner
            Partner testPartner = Partner.builder()
                    .id(CLIENT_ID)
                    .enabled(true)
                    .accessType(Partner.AccessType.BOTH)
                    .build();
            when(mock.findPartner(anyString())).thenReturn(Optional.of(testPartner));

            // Mock virtual file - allow any file with test directories
            VirtualFile testFile = VirtualFile.builder()
                    .id("*")
                    .enabled(true)
                    .direction(VirtualFile.Direction.BOTH)
                    .receiveDirectory(staticReceiveDirectory.toString())
                    .sendDirectory(staticSendDirectory.toString())
                    .receiveFilenamePattern("${filename}")
                    .overwrite(true)
                    .build();
            when(mock.findVirtualFile(anyString())).thenReturn(Optional.of(testFile));

            return mock;
        }
    }

    @Autowired
    private PesitServerProperties serverProperties;

    @Autowired
    private PesitServerManager serverManager;

    private PesitClientService clientService;
    private Path tempDir;
    private Path sendDirectory;
    private Path receiveDirectory;

    @BeforeAll
    void setUp() throws Exception {
        // Use the static directories
        tempDir = staticSendDirectory.getParent();
        sendDirectory = staticSendDirectory;
        receiveDirectory = staticReceiveDirectory;

        // Create and start a server instance
        PesitServerConfig serverConfig = new PesitServerConfig();
        serverConfig.setServerId(serverProperties.getServerId());
        serverConfig.setPort(serverProperties.getPort());
        serverConfig.setAutoStart(false);
        serverManager.createServer(serverConfig);
        serverManager.startServer(serverProperties.getServerId());

        // Wait for server to be ready
        Thread.sleep(500);

        // Configure client
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClientId(CLIENT_ID);
        clientConfig.setStrictMode(false);
        clientService = new PesitClientService(clientConfig);

        System.out.println("\n=== Test Setup Complete ===");
        System.out.println("Server ID: " + serverProperties.getServerId());
        System.out.println("Server port: " + serverProperties.getPort());
        System.out.println("Send directory: " + sendDirectory);
        System.out.println("Receive directory: " + receiveDirectory);
    }

    private String getServerId() {
        return serverProperties.getServerId();
    }

    @AfterAll
    void tearDown() throws Exception {
        // Stop the server
        try {
            serverManager.stopServer(serverProperties.getServerId());
        } catch (Exception e) {
            // Ignore
        }

        // Clean up temp directory
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception e) {
                        }
                    });
        }
        System.out.println("\n=== Test Teardown Complete ===");
    }

    private int getPort() {
        return serverProperties.getPort();
    }

    @Test
    @Order(1)
    @DisplayName("Test connection to server")
    void testConnection() throws Exception {
        System.out.println("\n=== CONNECTION TEST ===\n");

        boolean connected = clientService.testConnection(HOST, getPort(), getServerId());
        assertTrue(connected, "Connection test should succeed");

        System.out.println("  ✓ Connection test passed");
        System.out.println("\n✓✓✓ CONNECTION TEST PASSED ✓✓✓\n");
    }

    @Test
    @Order(2)
    @DisplayName("Test sending a file from client to server")
    void testSendFile() throws Exception {
        System.out.println("\n=== SEND FILE TEST ===\n");

        // Create a test file to send
        String testContent = "Hello, PeSIT! This is a test file.\n" +
                "Line 2 of the test file.\n" +
                "UUID: " + UUID.randomUUID() + "\n";
        Path sourceFile = tempDir.resolve("source_file.txt");
        Files.writeString(sourceFile, testContent);

        String remoteFilename = "test_send.txt";

        // Send the file
        TransferResult result = clientService.sendFile(
                HOST, getPort(), getServerId(),
                sourceFile, remoteFilename);

        // Verify transfer succeeded
        assertTrue(result.isSuccess(), "Transfer should succeed: " + result.getErrorMessage());
        assertEquals("SEND", result.getDirection());
        assertTrue(result.getBytesTransferred() > 0, "Should have transferred bytes");

        // Verify file was received on server
        Path receivedFile = receiveDirectory.resolve(remoteFilename);
        assertTrue(Files.exists(receivedFile), "Received file should exist at " + receivedFile);
        String receivedContent = Files.readString(receivedFile);
        assertEquals(testContent, receivedContent, "File content should match");

        System.out.println("  ✓ File sent successfully");
        System.out.println("    Filename: " + remoteFilename);
        System.out.println("    Bytes transferred: " + result.getBytesTransferred());
        System.out.println("    Duration: " + result.getDurationMs() + "ms");
        System.out.println("\n✓✓✓ SEND FILE TEST PASSED ✓✓✓\n");
    }

    @Test
    @Order(3)
    @DisplayName("Test sending a large file")
    void testSendLargeFile() throws Exception {
        System.out.println("\n=== SEND LARGE FILE TEST ===\n");

        // Create a larger test file (~50KB)
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            content.append("Line ").append(i).append(": ")
                    .append(UUID.randomUUID()).append("\n");
        }
        String testContent = content.toString();

        Path sourceFile = tempDir.resolve("large_file.txt");
        Files.writeString(sourceFile, testContent);

        String remoteFilename = "large_send.txt";

        // Send the file
        TransferResult result = clientService.sendFile(
                HOST, getPort(), getServerId(),
                sourceFile, remoteFilename);

        // Verify transfer succeeded
        assertTrue(result.isSuccess(), "Transfer should succeed: " + result.getErrorMessage());
        assertTrue(result.getBytesTransferred() > 40000, "Should have transferred >40KB");

        // Verify file was received on server
        Path receivedFile = receiveDirectory.resolve(remoteFilename);
        assertTrue(Files.exists(receivedFile), "Received file should exist");
        String receivedContent = Files.readString(receivedFile);
        assertEquals(testContent, receivedContent, "File content should match");

        System.out.println("  ✓ Large file sent successfully");
        System.out.println("    Filename: " + remoteFilename);
        System.out.println("    Bytes transferred: " + result.getBytesTransferred());
        System.out.println("    Duration: " + result.getDurationMs() + "ms");
        System.out.println("\n✓✓✓ SEND LARGE FILE TEST PASSED ✓✓✓\n");
    }

    @Test
    @Order(4)
    @DisplayName("Test receiving a file from server")
    void testReceiveFile() throws Exception {
        System.out.println("\n=== RECEIVE FILE TEST ===\n");

        // Create a test file on the server's send directory
        String testContent = "This file is from the server.\n" +
                "It will be downloaded by the client.\n" +
                "UUID: " + UUID.randomUUID() + "\n";
        String remoteFilename = "test_receive.txt";
        Path serverFile = sendDirectory.resolve(remoteFilename);
        Files.writeString(serverFile, testContent);

        // Receive the file
        Path localFile = tempDir.resolve("downloaded.txt");
        TransferResult result = clientService.receiveFile(
                HOST, getPort(), getServerId(),
                remoteFilename, localFile);

        // Verify transfer succeeded
        assertTrue(result.isSuccess(), "Transfer should succeed: " + result.getErrorMessage());
        assertEquals("RECEIVE", result.getDirection());
        assertTrue(result.getBytesTransferred() > 0, "Should have transferred bytes");

        // Verify file was received by client
        assertTrue(Files.exists(localFile), "Downloaded file should exist");
        String downloadedContent = Files.readString(localFile);
        assertEquals(testContent, downloadedContent, "File content should match");

        System.out.println("  ✓ File received successfully");
        System.out.println("    Local file: " + localFile);
        System.out.println("    Bytes transferred: " + result.getBytesTransferred());
        System.out.println("    Duration: " + result.getDurationMs() + "ms");
        System.out.println("\n✓✓✓ RECEIVE FILE TEST PASSED ✓✓✓\n");
    }

    @Test
    @Order(5)
    @DisplayName("Test round-trip: send then receive same file")
    void testRoundTrip() throws Exception {
        System.out.println("\n=== ROUND-TRIP TEST ===\n");

        // Create a test file
        String testContent = "Round-trip test content.\n" +
                "UUID: " + UUID.randomUUID() + "\n";
        Path sourceFile = tempDir.resolve("roundtrip_source.txt");
        Files.writeString(sourceFile, testContent);

        String filename = "roundtrip.txt";

        // Send the file
        TransferResult sendResult = clientService.sendFile(
                HOST, getPort(), getServerId(),
                sourceFile, filename);
        assertTrue(sendResult.isSuccess(), "Send should succeed: " + sendResult.getErrorMessage());

        // Copy received file to send directory (simulating server-side processing)
        Path receivedFile = receiveDirectory.resolve(filename);
        Path sendFile = sendDirectory.resolve(filename);
        Files.copy(receivedFile, sendFile);

        // Receive the file back
        Path downloadedFile = tempDir.resolve("roundtrip_downloaded.txt");
        TransferResult receiveResult = clientService.receiveFile(
                HOST, getPort(), getServerId(),
                filename, downloadedFile);
        assertTrue(receiveResult.isSuccess(), "Receive should succeed: " + receiveResult.getErrorMessage());

        // Verify content matches
        String downloadedContent = Files.readString(downloadedFile);
        assertEquals(testContent, downloadedContent, "Round-trip content should match");

        System.out.println("  ✓ Round-trip test passed");
        System.out.println("    Sent: " + sendResult.getBytesTransferred() + " bytes");
        System.out.println("    Received: " + receiveResult.getBytesTransferred() + " bytes");
        System.out.println("\n✓✓✓ ROUND-TRIP TEST PASSED ✓✓✓\n");
    }
}
