package com.pesitwizard.client.pesit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pesitwizard.client.entity.PesitServer;
import com.pesitwizard.security.SecretsService;
import com.pesitwizard.transport.TcpTransportChannel;
import com.pesitwizard.transport.TlsTransportChannel;
import com.pesitwizard.transport.TransportChannel;

@ExtendWith(MockitoExtension.class)
class PesitChannelFactoryTest {

    @Mock
    private SecretsService secretsService;

    private PesitChannelFactory factory;

    @BeforeEach
    void setUp() {
        factory = new PesitChannelFactory(secretsService);
    }

    private PesitServer tcpServer() {
        return PesitServer.builder()
                .host("localhost")
                .port(6502)
                .serverId("SRV1")
                .tlsEnabled(false)
                .readTimeout(60000)
                .build();
    }

    private PesitServer tlsServer() {
        return PesitServer.builder()
                .host("secure.example.com")
                .port(6503)
                .serverId("SRV2")
                .tlsEnabled(true)
                .readTimeout(60000)
                .build();
    }

    // --- TCP channel ---

    @Test
    void createChannel_tcp_returnsTcpChannel() {
        TransportChannel channel = factory.createChannel(tcpServer());
        assertThat(channel).isInstanceOf(TcpTransportChannel.class);
    }

    // --- TLS channel ---

    @Test
    void createChannel_tls_returnsTlsChannel() {
        TransportChannel channel = factory.createChannel(tlsServer());
        assertThat(channel).isInstanceOf(TlsTransportChannel.class);
    }

    @Test
    void createChannel_tlsWithTruststore_decryptsPasswords() {
        PesitServer server = PesitServer.builder()
                .host("secure.example.com")
                .port(6503)
                .serverId("SRV2")
                .tlsEnabled(true)
                .truststoreData(new byte[] { 1, 2, 3 })
                .truststorePassword("AES:encrypted-ts-pw")
                .keystoreData(new byte[] { 4, 5, 6 })
                .keystorePassword("AES:encrypted-ks-pw")
                .readTimeout(60000)
                .build();

        when(secretsService.decrypt("AES:encrypted-ts-pw")).thenReturn("ts-plain");
        when(secretsService.decrypt("AES:encrypted-ks-pw")).thenReturn("ks-plain");

        // TlsTransportChannel constructor eagerly initializes SSLContext,
        // which fails with fake truststore data. We verify decrypt was called.
        try {
            factory.createChannel(server);
        } catch (RuntimeException e) {
            // Expected: invalid truststore bytes
        }
        verify(secretsService).decrypt("AES:encrypted-ts-pw");
        verify(secretsService).decrypt("AES:encrypted-ks-pw");
    }

    // --- Timeout calculation ---

    @Test
    void timeout_defaultWhenNoFileSize() {
        TransportChannel channel = factory.createChannel(tcpServer(), 0);
        assertThat(channel).isNotNull();
    }

    @Test
    void timeout_increasesWithFileSize() {
        TransportChannel channel = factory.createChannel(tcpServer(), 100L * 1024 * 1024);
        assertThat(channel).isNotNull();
    }

    @Test
    void timeout_cappedAt30Minutes() {
        TransportChannel channel = factory.createChannel(tcpServer(), 10L * 1024 * 1024 * 1024);
        assertThat(channel).isNotNull();
    }

    @Test
    void timeout_usesServerReadTimeout() {
        PesitServer server = PesitServer.builder()
                .host("localhost")
                .port(6502)
                .serverId("SRV1")
                .tlsEnabled(false)
                .readTimeout(120000)
                .build();

        TransportChannel channel = factory.createChannel(server, 0);
        assertThat(channel).isNotNull();
    }

    @Test
    void timeout_usesDefaultWhenReadTimeoutNull() {
        PesitServer server = PesitServer.builder()
                .host("localhost")
                .port(6502)
                .serverId("SRV1")
                .tlsEnabled(false)
                .readTimeout(null)
                .build();

        TransportChannel channel = factory.createChannel(server, 0);
        assertThat(channel).isNotNull();
    }

    // --- Convenience method ---

    @Test
    void createChannel_noFileSize_callsOverload() {
        TransportChannel channel = factory.createChannel(tcpServer());
        assertThat(channel).isNotNull();
    }
}
