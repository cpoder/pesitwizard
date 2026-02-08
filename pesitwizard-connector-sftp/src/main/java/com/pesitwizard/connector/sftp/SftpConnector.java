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

public class SftpConnector implements StorageConnector {
    private static final Logger log = LoggerFactory.getLogger(SftpConnector.class);
    private String host, username, password, privateKeyPath, basePath, knownHostsFile;
    private int port;
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

        if (host == null) throw new ConnectorException(ConnectorException.ErrorCode.INVALID_CONFIG, "Host required");
        if (username == null) throw new ConnectorException(ConnectorException.ErrorCode.INVALID_CONFIG, "Username required");

        try {
            JSch jsch = new JSch();
            if (privateKeyPath != null) jsch.addIdentity(privateKeyPath);
            if (knownHostsFile != null && !knownHostsFile.isBlank()) {
                jsch.setKnownHosts(knownHostsFile);
            } else {
                log.warn("No knownHostsFile configured for SFTP connector (host={}). "
                        + "Host key verification is enabled but may fail without a known_hosts file. "
                        + "Configure 'knownHostsFile' with the path to a known_hosts file.", host);
            }
            session = jsch.getSession(username, host, port);
            if (password != null) session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "yes");
            session.connect(30000);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            initialized = true;
            log.info("SFTP connected: {}@{}:{}", username, host, port);
        } catch (JSchException e) {
            throw new ConnectorException(ConnectorException.ErrorCode.CONNECTION_FAILED, e.getMessage(), e);
        }
    }

    // S3-18: All channel operations are synchronized for thread safety
    @Override public boolean testConnection() throws ConnectorException {
        checkInit(); synchronized (channelLock) { try { channel.pwd(); return true; } catch (Exception e) { return false; } }
    }
    @Override public boolean exists(String path) throws ConnectorException {
        checkInit(); synchronized (channelLock) { try { channel.stat(resolve(path)); return true; } catch (SftpException e) { return false; } }
    }
    @Override public FileMetadata getMetadata(String path) throws ConnectorException {
        checkInit();
        synchronized (channelLock) {
            try {
                SftpATTRS a = channel.stat(resolve(path));
                return FileMetadata.builder().name(path).path(path).size(a.getSize())
                    .lastModified(java.time.Instant.ofEpochSecond(a.getMTime())).directory(a.isDir()).build();
            } catch (SftpException e) { throw new ConnectorException("Metadata error", e); }
        }
    }
    @Override @SuppressWarnings("unchecked")
    public List<FileMetadata> list(String path) throws ConnectorException {
        checkInit();
        synchronized (channelLock) {
            try {
                List<FileMetadata> r = new ArrayList<>();
                for (ChannelSftp.LsEntry e : (Vector<ChannelSftp.LsEntry>) channel.ls(resolve(path))) {
                    if (!e.getFilename().startsWith("."))
                        r.add(FileMetadata.builder().name(e.getFilename()).path(path+"/"+e.getFilename())
                            .size(e.getAttrs().getSize()).directory(e.getAttrs().isDir()).build());
                }
                return r;
            } catch (SftpException e) { throw new ConnectorException("List error", e); }
        }
    }
    @Override public InputStream read(String path) throws ConnectorException {
        checkInit(); synchronized (channelLock) { try { return channel.get(resolve(path)); } catch (SftpException e) { throw new ConnectorException("Read error", e); } }
    }
    @Override public InputStream read(String path, long offset) throws ConnectorException {
        checkInit(); synchronized (channelLock) { try { return channel.get(resolve(path), null, offset); } catch (SftpException e) { throw new ConnectorException("Read error", e); } }
    }
    @Override public OutputStream write(String path) throws ConnectorException { return write(path, false); }
    @Override public OutputStream write(String path, boolean append) throws ConnectorException {
        checkInit(); synchronized (channelLock) { try { return channel.put(resolve(path), append ? ChannelSftp.APPEND : ChannelSftp.OVERWRITE); } catch (SftpException e) { throw new ConnectorException("Write error", e); } }
    }
    @Override public void delete(String path) throws ConnectorException {
        checkInit(); synchronized (channelLock) { try { channel.rm(resolve(path)); } catch (SftpException e) { /* ignore */ } }
    }
    @Override public void mkdir(String path) throws ConnectorException {
        checkInit(); synchronized (channelLock) { try { channel.mkdir(resolve(path)); } catch (SftpException e) { /* ignore */ } }
    }
    @Override public void rename(String src, String dst) throws ConnectorException {
        checkInit(); synchronized (channelLock) { try { channel.rename(resolve(src), resolve(dst)); } catch (SftpException e) { throw new ConnectorException("Rename error", e); } }
    }
    @Override public List<ConfigParameter> getRequiredParameters() {
        return List.of(ConfigParameter.required("host", "SFTP host"), ConfigParameter.required("username", "Username"));
    }
    @Override public List<ConfigParameter> getOptionalParameters() {
        return List.of(
                ConfigParameter.password("password", "Password"),
                ConfigParameter.integer("port", "Port", 22),
                ConfigParameter.path("knownHostsFile", "Path to SSH known_hosts file for host key verification"),
                ConfigParameter.path("privateKey", "Path to SSH private key file"),
                ConfigParameter.optional("basePath", "Base directory on the remote server", "")
        );
    }
    @Override public boolean supportsResume() { return true; }
    @Override public void close() { if (channel != null) channel.disconnect(); if (session != null) session.disconnect(); initialized = false; }
    
    private void checkInit() throws ConnectorException { if (!initialized) throw new ConnectorException(ConnectorException.ErrorCode.INVALID_CONFIG, "Not initialized"); }

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
}
