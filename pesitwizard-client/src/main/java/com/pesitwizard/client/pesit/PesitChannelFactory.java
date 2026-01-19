package com.pesitwizard.client.pesit;

import org.springframework.stereotype.Component;

import com.pesitwizard.client.entity.PesitServer;
import com.pesitwizard.security.SecretsService;
import com.pesitwizard.transport.TcpTransportChannel;
import com.pesitwizard.transport.TlsTransportChannel;
import com.pesitwizard.transport.TransportChannel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory for creating PeSIT transport channels.
 * Handles TCP and TLS connections with appropriate timeout configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PesitChannelFactory {

    private static final int DEFAULT_TIMEOUT_MS = 60_000;
    private static final int MAX_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
    private static final long BYTES_PER_MINUTE = 50 * 1024 * 1024; // 50MB/min for timeout calc

    private final SecretsService secretsService;

    /**
     * Create a transport channel for the given server.
     */
    public TransportChannel createChannel(PesitServer server) {
        return createChannel(server, 0);
    }

    /**
     * Create a transport channel with timeout adjusted for file size.
     *
     * @param server   PeSIT server configuration
     * @param fileSize Expected file size in bytes (0 for default timeout)
     */
    public TransportChannel createChannel(PesitServer server, long fileSize) {
        int timeout = calculateTimeout(server, fileSize);

        if (fileSize > 0) {
            log.info("Creating channel to {}:{} with timeout {}ms for file size {} bytes",
                    server.getHost(), server.getPort(), timeout, fileSize);
        }

        if (server.isTlsEnabled()) {
            return createTlsChannel(server, timeout);
        }
        return createTcpChannel(server, timeout);
    }

    private TcpTransportChannel createTcpChannel(PesitServer server, int timeout) {
        TcpTransportChannel channel = new TcpTransportChannel(server.getHost(), server.getPort());
        channel.setReceiveTimeout(timeout);
        return channel;
    }

    private TlsTransportChannel createTlsChannel(PesitServer server, int timeout) {
        TlsTransportChannel tlsChannel;

        if (server.getTruststoreData() != null && server.getTruststoreData().length > 0) {
            tlsChannel = new TlsTransportChannel(
                    server.getHost(),
                    server.getPort(),
                    server.getTruststoreData(),
                    secretsService.decrypt(server.getTruststorePassword()),
                    server.getKeystoreData(),
                    secretsService.decrypt(server.getKeystorePassword()));
        } else {
            tlsChannel = new TlsTransportChannel(server.getHost(), server.getPort());
        }

        tlsChannel.setReceiveTimeout(timeout);
        return tlsChannel;
    }

    private int calculateTimeout(PesitServer server, long fileSize) {
        int baseTimeout = server.getReadTimeout() != null ? server.getReadTimeout() : DEFAULT_TIMEOUT_MS;

        if (fileSize <= 0) {
            return baseTimeout;
        }

        // Add 1 minute per 50MB
        int fileSizeTimeout = (int) ((fileSize / BYTES_PER_MINUTE) * 60_000);
        int totalTimeout = baseTimeout + fileSizeTimeout;

        return Math.min(totalTimeout, MAX_TIMEOUT_MS);
    }
}
