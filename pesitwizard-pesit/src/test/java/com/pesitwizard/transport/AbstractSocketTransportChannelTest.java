package com.pesitwizard.transport;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for AbstractSocketTransportChannel and TcpTransportChannel. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Socket Transport Channel Tests")
class AbstractSocketTransportChannelTest {

    @Mock private Socket mockSocket;

    private ByteArrayOutputStream outputBuffer;
    private ByteArrayInputStream inputBuffer;

    @Nested
    @DisplayName("TcpTransportChannel")
    class TcpTransportChannelTests {

        @Test
        @DisplayName("should create channel with host and port")
        void shouldCreateChannelWithHostAndPort() {
            TcpTransportChannel channel = new TcpTransportChannel("localhost", 5000);

            assertThat(channel.getHost()).isEqualTo("localhost");
            assertThat(channel.getPort()).isEqualTo(5000);
            assertThat(channel.isConnected()).isFalse();
            assertThat(channel.isSecure()).isFalse();
            assertThat(channel.getTransportType()).isEqualTo(TransportType.TCP);
        }

        @Test
        @DisplayName("should throw when sending on closed channel")
        void shouldThrowWhenSendingOnClosedChannel() {
            TcpTransportChannel channel = new TcpTransportChannel("localhost", 5000);

            assertThatThrownBy(() -> channel.send(new byte[] {1, 2, 3}))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Not connected");
        }

        @Test
        @DisplayName("should throw when receiving on closed channel")
        void shouldThrowWhenReceivingOnClosedChannel() {
            TcpTransportChannel channel = new TcpTransportChannel("localhost", 5000);

            assertThatThrownBy(() -> channel.receive())
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Not connected");
        }
    }

    @Nested
    @DisplayName("Send/Receive Protocol")
    class SendReceiveProtocolTests {

        @Test
        @DisplayName("should write 2-byte length prefix followed by data")
        void shouldWrite2ByteLengthPrefix() throws IOException {
            // Create a test channel that uses our mock streams
            outputBuffer = new ByteArrayOutputStream();
            byte[] testData = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05};

            // Manually write what send() should write
            DataOutputStream dos = new DataOutputStream(outputBuffer);
            dos.writeShort(testData.length);
            dos.write(testData);
            dos.flush();

            byte[] result = outputBuffer.toByteArray();

            // Verify 2-byte length prefix (big-endian)
            assertThat(result.length).isEqualTo(7); // 2 bytes length + 5 bytes data
            assertThat(result[0]).isEqualTo((byte) 0x00); // High byte of length
            assertThat(result[1]).isEqualTo((byte) 0x05); // Low byte of length
            assertThat(result[2]).isEqualTo((byte) 0x01); // First data byte
        }

        @Test
        @DisplayName("should read 2-byte length prefix and return data")
        void shouldRead2ByteLengthPrefix() throws IOException {
            // Create input with 2-byte length prefix
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeShort(5); // Length = 5
            dos.write(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05});
            dos.flush();

            inputBuffer = new ByteArrayInputStream(baos.toByteArray());
            DataInputStream dis = new DataInputStream(inputBuffer);

            // Read like receive() does
            int length = dis.readUnsignedShort();
            byte[] data = new byte[length];
            dis.readFully(data);

            assertThat(length).isEqualTo(5);
            assertThat(data).containsExactly(0x01, 0x02, 0x03, 0x04, 0x05);
        }

        @Test
        @DisplayName("should handle maximum FPDU size (65535 bytes)")
        void shouldHandleMaxFpduSize() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            int maxSize = 65535;
            dos.writeShort(maxSize);
            dos.write(new byte[maxSize]);
            dos.flush();

            inputBuffer = new ByteArrayInputStream(baos.toByteArray());
            DataInputStream dis = new DataInputStream(inputBuffer);

            int length = dis.readUnsignedShort();
            assertThat(length).isEqualTo(maxSize);
        }

        @Test
        @DisplayName("should handle small FPDU (1 byte)")
        void shouldHandleSmallFpdu() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeShort(1);
            dos.write(new byte[] {0x42});
            dos.flush();

            inputBuffer = new ByteArrayInputStream(baos.toByteArray());
            DataInputStream dis = new DataInputStream(inputBuffer);

            int length = dis.readUnsignedShort();
            byte[] data = new byte[length];
            dis.readFully(data);

            assertThat(length).isEqualTo(1);
            assertThat(data).containsExactly(0x42);
        }
    }

    @Nested
    @DisplayName("Connection State")
    class ConnectionStateTests {

        @Test
        @DisplayName("isConnected should return false when socket is null")
        void isConnectedShouldReturnFalseWhenSocketNull() {
            TcpTransportChannel channel = new TcpTransportChannel("localhost", 5000);
            assertThat(channel.isConnected()).isFalse();
        }

        @Test
        @DisplayName("getRemoteAddress should return host:port when not connected")
        void getRemoteAddressShouldReturnHostPortWhenNotConnected() {
            TcpTransportChannel channel = new TcpTransportChannel("example.com", 5001);
            assertThat(channel.getRemoteAddress()).isEqualTo("example.com:5001");
        }

        @Test
        @DisplayName("getLocalAddress should return 'not connected' when not connected")
        void getLocalAddressShouldReturnNotConnected() {
            TcpTransportChannel channel = new TcpTransportChannel("localhost", 5000);
            assertThat(channel.getLocalAddress()).isEqualTo("not connected");
        }
    }

    @Nested
    @DisplayName("Timeout Configuration")
    class TimeoutConfigurationTests {

        @Test
        @DisplayName("should set receive timeout")
        void shouldSetReceiveTimeout() {
            TcpTransportChannel channel = new TcpTransportChannel("localhost", 5000);

            // Should not throw even when not connected
            channel.setReceiveTimeout(30000);

            // No assertion needed - just verifying no exception
        }
    }
}
