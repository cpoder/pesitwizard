package com.pesitwizard.server.service;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;

import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.config.SslProperties;
import com.pesitwizard.server.entity.PesitServerConfig;
import com.pesitwizard.server.handler.PesitSessionHandler;
import com.pesitwizard.server.handler.TcpConnectionHandler;
import com.pesitwizard.server.ssl.SslContextFactory;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * A single PeSIT server instance.
 * This is a non-singleton version of PesitTcpServer that can be instantiated
 * multiple times.
 */
@Slf4j
@Getter
public class PesitServerInstance {

    private final PesitServerConfig config;
    private final PesitServerProperties properties;
    private final PesitSessionHandler sessionHandler;
    private final SslProperties sslProperties;
    private final SslContextFactory sslContextFactory;

    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private Thread acceptThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    /** Default drain timeout in seconds — time to wait for active transfers to complete */
    private static final int DEFAULT_DRAIN_TIMEOUT_SECONDS = 120;

    public PesitServerInstance(PesitServerConfig config, PesitServerProperties properties,
            PesitSessionHandler sessionHandler, SslProperties sslProperties,
            SslContextFactory sslContextFactory) {
        this.config = config;
        this.properties = properties;
        this.sessionHandler = sessionHandler;
        this.sslProperties = sslProperties;
        this.sslContextFactory = sslContextFactory;
    }

    /**
     * Start the server instance
     */
    public void start() throws IOException {
        if (running.get()) {
            throw new IllegalStateException("Server already running");
        }

        serverSocket = createServerSocket();
        executorService = Executors.newFixedThreadPool(properties.getMaxConnections());
        running.set(true);

        acceptThread = new Thread(this::acceptConnections, "pesit-" + config.getServerId() + "-accept");
        acceptThread.start();

        String protocol = sslProperties.isEnabled() ? "TLS" : "TCP/IP";
        String authMode = sslProperties.isEnabled() ? " (mTLS: " + sslProperties.getClientAuth() + ")" : "";
        log.info("[{}] PeSIT Server started on port {} (Hors-SIT profile, {}){}",
                config.getServerId(), properties.getPort(), protocol, authMode);
    }

    /**
     * Create server socket (plain or SSL based on configuration)
     */
    private ServerSocket createServerSocket() throws IOException {
        if (!sslProperties.isEnabled()) {
            return new ServerSocket(properties.getPort());
        }

        try {
            SSLContext sslContext = sslContextFactory.createSslContext(
                    sslProperties.getKeystoreName(),
                    sslProperties.getTruststoreName());

            SSLServerSocket sslServerSocket = (SSLServerSocket) sslContext
                    .getServerSocketFactory()
                    .createServerSocket(properties.getPort());

            // Configure client authentication (mTLS)
            switch (sslProperties.getClientAuth()) {
                case NEED:
                    sslServerSocket.setNeedClientAuth(true);
                    log.info("[{}] mTLS enabled: client certificate REQUIRED", config.getServerId());
                    break;
                case WANT:
                    sslServerSocket.setWantClientAuth(true);
                    log.info("[{}] mTLS enabled: client certificate REQUESTED", config.getServerId());
                    break;
                case NONE:
                default:
                    sslServerSocket.setNeedClientAuth(false);
                    sslServerSocket.setWantClientAuth(false);
                    log.info("[{}] TLS enabled: no client certificate required", config.getServerId());
                    break;
            }

            // Restrict TLS protocol versions (S3-01)
            if (sslProperties.getProtocols() != null && !sslProperties.getProtocols().isEmpty()) {
                sslServerSocket.setEnabledProtocols(
                        sslProperties.getProtocols().toArray(new String[0]));
                log.info("[{}] TLS protocols restricted to: {}", config.getServerId(), sslProperties.getProtocols());
            } else {
                // Fallback: only allow TLSv1.2 and TLSv1.3
                sslServerSocket.setEnabledProtocols(new String[] { "TLSv1.3", "TLSv1.2" });
                log.info("[{}] TLS protocols restricted to: [TLSv1.3, TLSv1.2]", config.getServerId());
            }

            // Configure cipher suites: apply denylist then allowlist (S3-02)
            String[] defaultCiphers = sslServerSocket.getEnabledCipherSuites();
            if (sslProperties.getCipherSuites() != null && !sslProperties.getCipherSuites().isEmpty()) {
                // Use explicit allowlist
                sslServerSocket.setEnabledCipherSuites(
                        sslProperties.getCipherSuites().toArray(new String[0]));
            } else {
                // Filter out insecure cipher suites from defaults
                String[] filtered = java.util.Arrays.stream(defaultCiphers)
                        .filter(c -> !isInsecureCipher(c))
                        .toArray(String[]::new);
                sslServerSocket.setEnabledCipherSuites(filtered);
                log.info("[{}] Filtered {} insecure cipher suites ({} -> {} remaining)",
                        config.getServerId(), defaultCiphers.length - filtered.length,
                        defaultCiphers.length, filtered.length);
            }

            return sslServerSocket;

        } catch (com.pesitwizard.server.ssl.SslConfigurationException e) {
            throw new IOException("Failed to create SSL server socket: " + e.getMessage(), e);
        }
    }

    /**
     * Drain the server: stop accepting new connections but wait for in-flight
     * transfers to complete (up to the specified timeout).
     *
     * @param timeoutSeconds max seconds to wait for active transfers to finish
     * @return true if all transfers completed, false if timed out
     */
    public boolean drain(int timeoutSeconds) {
        if (!running.get()) {
            return true;
        }

        log.info("[{}] Draining: stopping new connections, waiting for {} active transfers (timeout: {}s)",
                config.getServerId(), activeConnections.get(), timeoutSeconds);
        draining.set(true);

        // Close the server socket to stop accepting new connections
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.warn("[{}] Error closing server socket during drain: {}", config.getServerId(), e.getMessage());
        }

        // Wait for active transfers to complete
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (activeConnections.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        boolean drained = activeConnections.get() == 0;
        if (drained) {
            log.info("[{}] Drain complete: all transfers finished", config.getServerId());
        } else {
            log.warn("[{}] Drain timed out: {} transfers still active after {}s",
                    config.getServerId(), activeConnections.get(), timeoutSeconds);
        }
        return drained;
    }

    /**
     * Drain with default timeout
     */
    public boolean drain() {
        return drain(DEFAULT_DRAIN_TIMEOUT_SECONDS);
    }

    /**
     * Stop the server instance
     */
    public void stop() {
        if (!running.get()) {
            return;
        }

        log.info("[{}] Stopping PeSIT server...", config.getServerId());
        running.set(false);

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.warn("[{}] Error closing server socket: {}", config.getServerId(), e.getMessage());
        }

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (acceptThread != null) {
            acceptThread.interrupt();
        }

        log.info("[{}] PeSIT server stopped", config.getServerId());
    }

    /**
     * Check if server is running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Get active connection count
     */
    public int getActiveConnections() {
        return activeConnections.get();
    }

    private void acceptConnections() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();

                if (draining.get()) {
                    log.info("[{}] Rejecting connection during drain from {}",
                            config.getServerId(), clientSocket.getRemoteSocketAddress());
                    clientSocket.close();
                    continue;
                }

                if (activeConnections.get() >= properties.getMaxConnections()) {
                    log.warn("[{}] Max connections reached ({}), rejecting connection from {}",
                            config.getServerId(), properties.getMaxConnections(),
                            clientSocket.getRemoteSocketAddress());
                    clientSocket.close();
                    continue;
                }

                activeConnections.incrementAndGet();
                log.info("[{}] Accepted connection from {} (active: {})",
                        config.getServerId(), clientSocket.getRemoteSocketAddress(),
                        activeConnections.get());

                TcpConnectionHandler handler = new TcpConnectionHandler(
                        clientSocket, sessionHandler, properties, config.getServerId());

                executorService.submit(() -> {
                    try {
                        handler.run();
                    } finally {
                        activeConnections.decrementAndGet();
                        log.debug("[{}] Connection handler finished (active: {})",
                                config.getServerId(), activeConnections.get());
                    }
                });

            } catch (SocketException e) {
                if (running.get()) {
                    log.error("[{}] Socket error: {}", config.getServerId(), e.getMessage());
                }
            } catch (IOException e) {
                if (running.get()) {
                    log.error("[{}] Error accepting connection: {}", config.getServerId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Check if a cipher suite is insecure and should be denied (S3-02).
     * Denylists NULL, anon, EXPORT, DES, 3DES, RC4 cipher suites.
     */
    private static boolean isInsecureCipher(String cipher) {
        String upper = cipher.toUpperCase();
        return upper.contains("_NULL_") || upper.contains("_NULL")
                || upper.contains("_ANON_") || upper.contains("_ANON")
                || upper.contains("EXPORT")
                || upper.contains("_DES_") || upper.contains("_DES40_")
                || upper.contains("_3DES_") || upper.contains("DES_CBC")
                || upper.contains("_RC4_") || upper.contains("_RC4");
    }
}
