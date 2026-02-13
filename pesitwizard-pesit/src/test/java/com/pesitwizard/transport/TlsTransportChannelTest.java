package com.pesitwizard.transport;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for TlsTransportChannel. */
@DisplayName("TlsTransportChannel Tests")
class TlsTransportChannelTest {

    @Nested
    @DisplayName("Channel Creation")
    class ChannelCreationTests {

        @Test
        @DisplayName("should create channel with host and port using default SSL context")
        void shouldCreateChannelWithHostAndPort() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);

            assertThat(channel.getHost()).isEqualTo("localhost");
            assertThat(channel.getPort()).isEqualTo(5000);
            assertThat(channel.isConnected()).isFalse();
            assertThat(channel.isSecure()).isTrue();
            assertThat(channel.getTransportType()).isEqualTo(TransportType.SSL);
        }

        @Test
        @DisplayName("should throw when creating with null truststore data")
        void shouldThrowWhenTruststoreDataNull() {
            assertThatThrownBy(() -> new TlsTransportChannel("localhost", 5000, null, "password"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should throw when creating with invalid truststore data")
        void shouldThrowWhenTruststoreDataInvalid() {
            byte[] invalidData = new byte[] {0x00, 0x01, 0x02};

            assertThatThrownBy(
                            () ->
                                    new TlsTransportChannel(
                                            "localhost", 5000, invalidData, "password"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to initialize SSL context");
        }
    }

    @Nested
    @DisplayName("Hostname Verification")
    class HostnameVerificationTests {

        @Test
        @DisplayName("hostname verification should be enabled by default")
        void hostnameVerificationEnabledByDefault() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);
            assertThat(channel.isHostnameVerification()).isTrue();
        }

        @Test
        @DisplayName("hostname verification can be disabled")
        void hostnameVerificationCanBeDisabled() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);
            channel.setHostnameVerification(false);
            assertThat(channel.isHostnameVerification()).isFalse();
        }

        @Test
        @DisplayName("hostname verification can be re-enabled after disabling")
        void hostnameVerificationCanBeReEnabled() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);
            channel.setHostnameVerification(false);
            channel.setHostnameVerification(true);
            assertThat(channel.isHostnameVerification()).isTrue();
        }
    }

    @Nested
    @DisplayName("Connection State")
    class ConnectionStateTests {

        @Test
        @DisplayName("should return false for isConnected when not connected")
        void shouldReturnFalseWhenNotConnected() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);
            assertThat(channel.isConnected()).isFalse();
        }

        @Test
        @DisplayName("should return null for getSession when not connected")
        void shouldReturnNullSessionWhenNotConnected() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);
            assertThat(channel.getSession()).isNull();
        }

        @Test
        @DisplayName("isSecure should always return true")
        void isSecureShouldReturnTrue() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);
            assertThat(channel.isSecure()).isTrue();
        }

        @Test
        @DisplayName("getTransportType should return SSL")
        void getTransportTypeShouldReturnSsl() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);
            assertThat(channel.getTransportType()).isEqualTo(TransportType.SSL);
        }
    }

    @Nested
    @DisplayName("Framing Mode")
    class FramingModeTests {

        @Test
        @DisplayName("TLS framing (no external prefix) should be the default")
        void tlsFramingEnabledByDefault() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);
            assertThat(channel.isStandardFraming()).isFalse();
        }

        @Test
        @DisplayName("should switch to legacy TCP-style framing")
        void shouldSwitchToLegacyFraming() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);
            channel.setStandardFraming(true);
            assertThat(channel.isStandardFraming()).isTrue();
        }

        @Test
        @DisplayName("should switch back to TLS framing")
        void shouldSwitchBackToTlsFraming() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);
            channel.setStandardFraming(true);
            channel.setStandardFraming(false);
            assertThat(channel.isStandardFraming()).isFalse();
        }

        @Test
        @DisplayName("TLS framing should throw on closed channel")
        void tlsFramingShouldThrowOnClosedChannel() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);

            assertThatThrownBy(() -> channel.send(new byte[] {1, 2, 3}))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("Not connected");

            assertThatThrownBy(() -> channel.receive())
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("Not connected");
        }
    }

    @Nested
    @DisplayName("Operations on Closed Channel")
    class ClosedChannelOperationsTests {

        @Test
        @DisplayName("should throw when sending on closed channel")
        void shouldThrowWhenSendingOnClosedChannel() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);

            assertThatThrownBy(() -> channel.send(new byte[] {1, 2, 3}))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("Not connected");
        }

        @Test
        @DisplayName("should throw when receiving on closed channel")
        void shouldThrowWhenReceivingOnClosedChannel() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);

            assertThatThrownBy(() -> channel.receive())
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("Not connected");
        }

        @Test
        @DisplayName("close should not throw on already closed channel")
        void closeShouldNotThrowOnClosedChannel() {
            TlsTransportChannel channel = new TlsTransportChannel("localhost", 5000);

            assertThatCode(() -> channel.close()).doesNotThrowAnyException();
            assertThatCode(() -> channel.close()).doesNotThrowAnyException(); // Double close
        }
    }
}
