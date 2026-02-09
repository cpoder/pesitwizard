package com.pesitwizard.connector.sftp;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.pesitwizard.connector.ConfigParameter;
import com.pesitwizard.connector.ConnectorException;
import com.pesitwizard.connector.FileMetadata;
import com.pesitwizard.connector.StorageConnector;

/**
 * SFTP storage connector with connection resilience.
 * <p>
 * Features:
 * <ul>
 *   <li>Automatic reconnection on stale/broken connections</li>
 *   <li>Retry with exponential backoff for transient failures</li>
 *   <li>SSH keepalive to prevent idle disconnects</li>
 *   <li>Thread-safe channel access</li>
 * </ul>
 */
public class SftpConnector implements StorageConnector {
    private static final Logger log = LoggerFactory.getLogger(SftpConnector.class);

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 30_000;
    private static final int DEFAULT_KEEPALIVE_INTERVAL_S = 60;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 500;

    private String host, username, password, privateKeyPath, basePath, knownHostsFile;
    private int port;
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS;
    private int keepaliveInterval = DEFAULT_KEEPALIVE_INTERVAL_S;
    private int maxRetries = DEFAULT_MAX_RETRIES;

    private JSch jsch;
    private Session session;
    private ChannelSftp channel;
    private boolean initialized = false;
    // S3-18: Lock for thread-safe access to the shared ChannelSftp
    private final Object channelLock = new Object();

    @Override public String getType() { return "sftp"; }
    @Override public String getName() { return "SFTP"; }
    @Override public String getVersion() { return "1.0.0"; }

    @Override
    public void initialize(Map<String, String> config) throws ConnectorException {
        host = config.get("host");
        port = Integer.parseInt(config.getOrDefault("port", "22"));
        username = config.get("username");
        password = config.get("password");
        privateKeyPath = config.get("privateKey");
        basePath = config.getOrDefault("basePath", "");
        knownHostsFile = config.get("knownHostsFile");
        connectTimeout = Integer.parseInt(config.getOrDefault("connectTimeout",
                String.valueOf(DEFAULT_CONNECT_TIMEOUT_MS)));
        keepaliveInterval = Integer.parseInt(config.getOrDefault("keepaliveInterval",
                String.valueOf(DEFAULT_KEEPALIVE_INTERVAL_S)));
        maxRetries = Integer.parseInt(config.getOrDefault("maxRetries",
                String.valueOf(DEFAULT_MAX_RETRIES)));

        if (host == null) throw new ConnectorException(ConnectorException.ErrorCode.INVALID_CONFIG, "Host required");
        if (username == null) throw new ConnectorException(ConnectorException.ErrorCode.INVALID_CONFIG, "Username required");

        try {
            jsch = new JSch();
            if (privateKeyPath != null) jsch.addIdentity(privateKeyPath);
            if (knownHostsFile != null && !knownHostsFile.isBlank()) {
                jsch.setKnownHosts(knownHostsFile);
            } else {
                log.warn("No knownHostsFile configured for SFTP connector (host={}). "
                        + "Host key verification is enabled but may fail without a known_hosts file. "
                        + "Configure 'knownHostsFile' with the path to a known_hosts file.", host);
            }
        } catch (JSchException e) {
            throw new ConnectorException(ConnectorException.ErrorCode.CONNECTION_FAILED,
                    "Failed to initialize JSch: " + e.getMessage(), e);
        }

        connect();
        initialized = true;
    }

    /**
     * Establish SSH session and SFTP channel.
     */
    private void connect() throws ConnectorException {
        try {
            disconnect();

            session = jsch.getSession(username, host, port);
            if (password != null) session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "yes");
            // BL-12: SSH keepalive to prevent idle disconnects
            session.setServerAliveInterval(keepaliveInterval * 1000);
            session.setServerAliveCountMax(3);
            session.connect(connectTimeout);

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(connectTimeout);

            log.info("SFTP connected: {}@{}:{}", username, host, port);
        } catch (JSchException e) {
            throw new ConnectorException(ConnectorException.ErrorCode.CONNECTION_FAILED, e.getMessage(), e);
        }
    }

    /**
     * Disconnect session and channel without changing initialized state.
     */
    private void disconnect() {
        try {
            if (channel != null && channel.isConnected()) channel.disconnect();
        } catch (Exception e) {
            log.debug("Error disconnecting SFTP channel: {}", e.getMessage());
        }
        try {
            if (session != null && session.isConnected()) session.disconnect();
        } catch (Exception e) {
            log.debug("Error disconnecting SSH session: {}", e.getMessage());
        }
        channel = null;
        session = null;
    }

    /**
     * Check if the connection is alive and reconnect if needed.
     */
    private void ensureConnected() throws ConnectorException {
        if (session != null && session.isConnected()
                && channel != null && channel.isConnected()) {
            return;
        }
        log.info("SFTP connection lost to {}@{}:{}, reconnecting...", username, host, port);
        connect();
    }

    /**
     * Execute an SFTP operation with automatic reconnection and retry.
     * On transient failures (connection lost, channel closed), reconnects and retries
     * with exponential backoff up to {@code maxRetries} times.
     */
    private <T> T withRetry(String operationName, SftpOperation<T> operation) throws ConnectorException {
        checkInit();
        ConnectorException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            synchronized (channelLock) {
                try {
                    ensureConnected();
                    return operation.execute(channel);
                } catch (ConnectorException e) {
                    // Don't retry connector-level errors (path validation, config errors)
                    throw e;
                } catch (SftpException e) {
                    // Non-transient SFTP errors (file not found, permission denied) — don't retry
                    if (!isTransient(e)) {
                        throw new ConnectorException(mapErrorCode(e), operationName + " error: " + e.getMessage(), e);
                    }
                    lastException = new ConnectorException(
                            ConnectorException.ErrorCode.CONNECTION_FAILED,
                            operationName + " failed (attempt " + (attempt + 1) + "): " + e.getMessage(), e);
                } catch (Exception e) {
                    // JSchException, IOException, etc. — transient, retry
                    lastException = new ConnectorException(
                            ConnectorException.ErrorCode.CONNECTION_FAILED,
                            operationName + " failed (attempt " + (attempt + 1) + "): " + e.getMessage(), e);
                }
            }

            if (attempt < maxRetries) {
                long backoff = INITIAL_BACKOFF_MS * (1L << attempt);
                log.warn("SFTP {} failed (attempt {}/{}), retrying in {}ms...",
                        operationName, attempt + 1, maxRetries + 1, backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ConnectorException(ConnectorException.ErrorCode.CONNECTION_FAILED,
                            operationName + " interrupted during retry backoff", ie);
                }
                // Force reconnect before next attempt
                synchronized (channelLock) {
                    disconnect();
                }
            }
        }

        throw lastException;
    }

    /**
     * Execute a void SFTP operation with retry.
     */
    private void withRetryVoid(String operationName, SftpVoidOperation operation) throws ConnectorException {
        withRetry(operationName, ch -> { operation.execute(ch); return null; });
    }

    /**
     * Determine if an SFTP error is transient (connection-related) vs permanent (file-level).
     */
    private static boolean isTransient(SftpException e) {
        // SSH_FX_NO_SUCH_FILE, SSH_FX_PERMISSION_DENIED, SSH_FX_FAILURE with specific file errors
        // are not transient — retrying won't help
        int id = e.id;
        return id != ChannelSftp.SSH_FX_NO_SUCH_FILE
                && id != ChannelSftp.SSH_FX_PERMISSION_DENIED
                && id != ChannelSftp.SSH_FX_OP_UNSUPPORTED;
    }

    /**
     * Map SFTP error codes to ConnectorException error codes.
     */
    private static ConnectorException.ErrorCode mapErrorCode(SftpException e) {
        return switch (e.id) {
            case ChannelSftp.SSH_FX_NO_SUCH_FILE -> ConnectorException.ErrorCode.FILE_NOT_FOUND;
            case ChannelSftp.SSH_FX_PERMISSION_DENIED -> ConnectorException.ErrorCode.PERMISSION_DENIED;
            case ChannelSftp.SSH_FX_OP_UNSUPPORTED -> ConnectorException.ErrorCode.NOT_SUPPORTED;
            default -> ConnectorException.ErrorCode.UNKNOWN;
        };
    }

    // S3-18: All channel operations are synchronized for thread safety (via withRetry)
    @Override
    public boolean testConnection() throws ConnectorException {
        checkInit();
        synchronized (channelLock) {
            try {
                ensureConnected();
                channel.pwd();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    @Override
    public boolean exists(String path) throws ConnectorException {
        try {
            return withRetry("exists", ch -> { ch.stat(resolve(path)); return true; });
        } catch (ConnectorException e) {
            if (e.getErrorCode() == ConnectorException.ErrorCode.FILE_NOT_FOUND) return false;
            throw e;
        }
    }

    @Override
    public FileMetadata getMetadata(String path) throws ConnectorException {
        return withRetry("getMetadata", ch -> {
            SftpATTRS a = ch.stat(resolve(path));
            return FileMetadata.builder().name(path).path(path).size(a.getSize())
                    .lastModified(java.time.Instant.ofEpochSecond(a.getMTime())).directory(a.isDir()).build();
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<FileMetadata> list(String path) throws ConnectorException {
        return withRetry("list", ch -> {
            List<FileMetadata> r = new ArrayList<>();
            for (ChannelSftp.LsEntry e : (Vector<ChannelSftp.LsEntry>) ch.ls(resolve(path))) {
                if (!e.getFilename().startsWith("."))
                    r.add(FileMetadata.builder().name(e.getFilename()).path(path + "/" + e.getFilename())
                            .size(e.getAttrs().getSize()).directory(e.getAttrs().isDir()).build());
            }
            return r;
        });
    }

    @Override
    public InputStream read(String path) throws ConnectorException {
        return withRetry("read", ch -> ch.get(resolve(path)));
    }

    @Override
    public InputStream read(String path, long offset) throws ConnectorException {
        return withRetry("read", ch -> ch.get(resolve(path), null, offset));
    }

    @Override
    public OutputStream write(String path) throws ConnectorException {
        return write(path, false);
    }

    @Override
    public OutputStream write(String path, boolean append) throws ConnectorException {
        return withRetry("write", ch -> ch.put(resolve(path), append ? ChannelSftp.APPEND : ChannelSftp.OVERWRITE));
    }

    @Override
    public void delete(String path) throws ConnectorException {
        withRetryVoid("delete", ch -> ch.rm(resolve(path)));
    }

    @Override
    public void mkdir(String path) throws ConnectorException {
        try {
            withRetryVoid("mkdir", ch -> ch.mkdir(resolve(path)));
        } catch (ConnectorException e) {
            // Ignore "already exists" — mkdir is idempotent
            if (e.getCause() instanceof SftpException se
                    && se.id == ChannelSftp.SSH_FX_FAILURE
                    && exists(path)) {
                return;
            }
            throw e;
        }
    }

    @Override
    public void rename(String src, String dst) throws ConnectorException {
        withRetryVoid("rename", ch -> ch.rename(resolve(src), resolve(dst)));
    }

    @Override
    public List<ConfigParameter> getRequiredParameters() {
        return List.of(ConfigParameter.required("host", "SFTP host"), ConfigParameter.required("username", "Username"));
    }

    @Override
    public List<ConfigParameter> getOptionalParameters() {
        return List.of(
                ConfigParameter.password("password", "Password"),
                ConfigParameter.integer("port", "Port", 22),
                ConfigParameter.path("knownHostsFile", "Path to SSH known_hosts file for host key verification"),
                ConfigParameter.path("privateKey", "Path to SSH private key file"),
                ConfigParameter.optional("basePath", "Base directory on the remote server", ""),
                ConfigParameter.integer("connectTimeout", "Connection timeout in milliseconds", DEFAULT_CONNECT_TIMEOUT_MS),
                ConfigParameter.integer("keepaliveInterval", "SSH keepalive interval in seconds", DEFAULT_KEEPALIVE_INTERVAL_S),
                ConfigParameter.integer("maxRetries", "Max retry attempts for transient failures", DEFAULT_MAX_RETRIES)
        );
    }

    @Override public boolean supportsResume() { return true; }

    @Override
    public void close() {
        synchronized (channelLock) {
            disconnect();
            initialized = false;
        }
    }

    private void checkInit() throws ConnectorException {
        if (!initialized) throw new ConnectorException(ConnectorException.ErrorCode.INVALID_CONFIG, "Not initialized");
    }

    /**
     * Resolve path and validate against path traversal (S3-17).
     */
    private String resolve(String p) throws ConnectorException {
        if (p == null || p.contains("..") || p.contains("\0")) {
            throw new ConnectorException(ConnectorException.ErrorCode.INVALID_PATH,
                    "Invalid path: traversal or null bytes not allowed");
        }
        String resolved = basePath.isEmpty() ? p : basePath + "/" + p;
        // Normalize and verify resolved path stays within basePath
        if (!basePath.isEmpty()) {
            java.nio.file.Path base = java.nio.file.Paths.get(basePath).normalize();
            java.nio.file.Path target = java.nio.file.Paths.get(resolved).normalize();
            if (!target.startsWith(base)) {
                throw new ConnectorException(ConnectorException.ErrorCode.INVALID_PATH,
                        "Path escapes base directory");
            }
        }
        return resolved;
    }

    @FunctionalInterface
    interface SftpOperation<T> {
        T execute(ChannelSftp channel) throws Exception;
    }

    @FunctionalInterface
    interface SftpVoidOperation {
        void execute(ChannelSftp channel) throws Exception;
    }
}
