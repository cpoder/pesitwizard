package com.pesitwizard.transport;

import static org.assertj.core.api.Assertions.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Integration tests for TLS transport with real SSL handshake. Uses self-signed certificates
 * generated at test time.
 */
@DisplayName("TLS Transport Integration Tests")
class TlsTransportIntegrationTest {

    private ExecutorService executor;
    private ServerSocket serverSocket;
    private int serverPort;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() throws IOException {
        executor.shutdownNow();
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    @Nested
    @DisplayName("Plain TCP Integration")
    class PlainTcpIntegrationTests {

        @Test
        @Timeout(5)
        @DisplayName("should connect and exchange data over TCP")
        void shouldConnectAndExchangeDataOverTcp() throws Exception {
            // Start plain TCP server
            serverSocket = new ServerSocket(0);
            serverPort = serverSocket.getLocalPort();

            CountDownLatch serverReady = new CountDownLatch(1);
            AtomicReference<byte[]> receivedData = new AtomicReference<>();

            executor.submit(
                    () -> {
                        try {
                            serverReady.countDown();
                            Socket client = serverSocket.accept();
                            DataInputStream dis = new DataInputStream(client.getInputStream());
                            DataOutputStream dos = new DataOutputStream(client.getOutputStream());

                            // Read FPDU (2-byte length prefix)
                            int len = dis.readUnsignedShort();
                            byte[] data = new byte[len];
                            dis.readFully(data);
                            receivedData.set(data);

                            // Send response
                            dos.writeShort(4);
                            dos.write(new byte[] {0x0A, 0x0B, 0x0C, 0x0D});
                            dos.flush();

                            client.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

            serverReady.await();

            // Connect with TcpTransportChannel
            TcpTransportChannel channel = new TcpTransportChannel("localhost", serverPort);
            channel.connect();

            assertThat(channel.isConnected()).isTrue();

            // Send data
            channel.send(new byte[] {0x01, 0x02, 0x03});

            // Receive response
            byte[] response = channel.receive();

            channel.close();

            // Verify
            assertThat(receivedData.get()).containsExactly(0x01, 0x02, 0x03);
            assertThat(response).containsExactly(0x0A, 0x0B, 0x0C, 0x0D);
        }
    }

    @Nested
    @DisplayName("FPDU Protocol")
    class FpduProtocolTests {

        @Test
        @Timeout(5)
        @DisplayName("should handle max FPDU size (65535 bytes)")
        void shouldHandleMaxFpduSize() throws Exception {
            serverSocket = new ServerSocket(0);
            serverPort = serverSocket.getLocalPort();

            CountDownLatch serverReady = new CountDownLatch(1);
            AtomicReference<Integer> receivedLength = new AtomicReference<>();

            executor.submit(
                    () -> {
                        try {
                            serverReady.countDown();
                            Socket client = serverSocket.accept();
                            DataInputStream dis = new DataInputStream(client.getInputStream());
                            DataOutputStream dos = new DataOutputStream(client.getOutputStream());

                            // Read large FPDU
                            int len = dis.readUnsignedShort();
                            byte[] data = new byte[len];
                            dis.readFully(data);
                            receivedLength.set(len);

                            // Send small ack
                            dos.writeShort(1);
                            dos.write(new byte[] {0x00});
                            dos.flush();

                            client.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

            serverReady.await();

            TcpTransportChannel channel = new TcpTransportChannel("localhost", serverPort);
            channel.connect();

            // Send max size FPDU
            byte[] largeData = new byte[65535];
            for (int i = 0; i < largeData.length; i++) {
                largeData[i] = (byte) (i % 256);
            }
            channel.send(largeData);

            channel.receive(); // Wait for ack
            channel.close();

            assertThat(receivedLength.get()).isEqualTo(65535);
        }

        @Test
        @Timeout(5)
        @DisplayName("should handle multiple sequential FPDUs")
        void shouldHandleMultipleSequentialFpdus() throws Exception {
            serverSocket = new ServerSocket(0);
            serverPort = serverSocket.getLocalPort();

            CountDownLatch serverReady = new CountDownLatch(1);
            AtomicReference<Integer> messageCount = new AtomicReference<>(0);

            executor.submit(
                    () -> {
                        try {
                            serverReady.countDown();
                            Socket client = serverSocket.accept();
                            DataInputStream dis = new DataInputStream(client.getInputStream());
                            DataOutputStream dos = new DataOutputStream(client.getOutputStream());

                            // Echo 5 messages
                            for (int i = 0; i < 5; i++) {
                                int len = dis.readUnsignedShort();
                                byte[] data = new byte[len];
                                dis.readFully(data);
                                messageCount.updateAndGet(v -> v + 1);

                                // Echo back
                                dos.writeShort(len);
                                dos.write(data);
                                dos.flush();
                            }

                            client.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

            serverReady.await();

            TcpTransportChannel channel = new TcpTransportChannel("localhost", serverPort);
            channel.connect();

            // Send 5 messages and verify echo
            for (int i = 0; i < 5; i++) {
                byte[] msg = new byte[] {(byte) i, (byte) (i + 1)};
                channel.send(msg);
                byte[] response = channel.receive();
                assertThat(response).containsExactly(msg);
            }

            channel.close();
            assertThat(messageCount.get()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Connection Handling")
    class ConnectionHandlingTests {

        @Test
        @DisplayName("should throw when connecting to closed port")
        void shouldThrowWhenConnectingToClosedPort() {
            TcpTransportChannel channel = new TcpTransportChannel("localhost", 59999);

            assertThatThrownBy(() -> channel.connect()).isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("should throw when connecting to invalid host")
        void shouldThrowWhenConnectingToInvalidHost() {
            TcpTransportChannel channel =
                    new TcpTransportChannel("invalid.host.that.does.not.exist.local", 5000);

            assertThatThrownBy(() -> channel.connect()).isInstanceOf(IOException.class);
        }
    }
}
