package com.pesitwizard.connector.local;

import com.pesitwizard.connector.ConfigParameter;
import com.pesitwizard.connector.ConnectorException;
import com.pesitwizard.connector.FileMetadata;
import com.pesitwizard.connector.StorageConnector;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Local filesystem storage connector. */
public class LocalFileConnector implements StorageConnector {

    private static final Logger log = LoggerFactory.getLogger(LocalFileConnector.class);

    private Path basePath;
    private boolean initialized = false;

    @Override
    public String getType() {
        return "local";
    }

    @Override
    public String getName() {
        return "Local Filesystem";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void initialize(Map<String, String> config) throws ConnectorException {
        String baseDir = config.getOrDefault("basePath", ".");
        this.basePath = Path.of(baseDir).toAbsolutePath().normalize();

        if (!Files.exists(basePath)) {
            try {
                Files.createDirectories(basePath);
                log.info("Created base directory: {}", basePath);
            } catch (IOException e) {
                throw new ConnectorException(
                        ConnectorException.ErrorCode.INVALID_PATH,
                        "Cannot create base directory: " + basePath,
                        e);
            }
        }

        if (!Files.isDirectory(basePath)) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.INVALID_PATH,
                    "Base path is not a directory: " + basePath);
        }

        this.initialized = true;
        log.info("Local connector initialized at: {}", basePath);
    }

    @Override
    public boolean testConnection() throws ConnectorException {
        checkInitialized();
        return Files.isReadable(basePath) && Files.isWritable(basePath);
    }

    @Override
    public boolean exists(String path) throws ConnectorException {
        checkInitialized();
        return Files.exists(resolvePath(path));
    }

    @Override
    public FileMetadata getMetadata(String path) throws ConnectorException {
        checkInitialized();
        Path resolved = resolvePath(path);

        if (!Files.exists(resolved)) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.FILE_NOT_FOUND, "File not found: " + path);
        }

        try {
            BasicFileAttributes attrs = Files.readAttributes(resolved, BasicFileAttributes.class);
            Path fileName = resolved.getFileName();
            return FileMetadata.builder()
                    .name(fileName != null ? fileName.toString() : path)
                    .path(path)
                    .size(attrs.size())
                    .lastModified(attrs.lastModifiedTime().toInstant())
                    .createdAt(attrs.creationTime().toInstant())
                    .directory(attrs.isDirectory())
                    .build();
        } catch (IOException e) {
            throw new ConnectorException("Failed to read file attributes: " + path, e);
        }
    }

    @Override
    public List<FileMetadata> list(String path) throws ConnectorException {
        checkInitialized();
        Path resolved = resolvePath(path);

        if (!Files.isDirectory(resolved)) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.INVALID_PATH, "Not a directory: " + path);
        }

        try (Stream<Path> files = Files.list(resolved)) {
            return files.map(
                            p -> {
                                try {
                                    BasicFileAttributes attrs =
                                            Files.readAttributes(p, BasicFileAttributes.class);
                                    // Use absolute path to preserve leading /
                                    String filePath = p.toAbsolutePath().toString();
                                    return FileMetadata.builder()
                                            .name(p.getFileName().toString())
                                            .path(filePath)
                                            .size(attrs.size())
                                            .lastModified(attrs.lastModifiedTime().toInstant())
                                            .directory(attrs.isDirectory())
                                            .build();
                                } catch (IOException e) {
                                    return FileMetadata.builder()
                                            .name(p.getFileName().toString())
                                            .path(p.toAbsolutePath().toString())
                                            .build();
                                }
                            })
                    .toList();
        } catch (IOException e) {
            throw new ConnectorException("Failed to list directory: " + path, e);
        }
    }

    @Override
    public InputStream read(String path) throws ConnectorException {
        checkInitialized();
        Path resolved = resolvePath(path);

        if (!Files.exists(resolved)) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.FILE_NOT_FOUND, "File not found: " + path);
        }

        try {
            return Files.newInputStream(resolved);
        } catch (IOException e) {
            throw new ConnectorException("Failed to open file: " + path, e);
        }
    }

    @Override
    public InputStream read(String path, long offset) throws ConnectorException {
        checkInitialized();
        Path resolved = resolvePath(path);

        if (!Files.exists(resolved)) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.FILE_NOT_FOUND, "File not found: " + path);
        }

        try {
            RandomAccessFile raf = new RandomAccessFile(resolved.toFile(), "r");
            raf.seek(offset);
            return new RandomAccessFileInputStream(raf);
        } catch (IOException e) {
            throw new ConnectorException("Failed to open file at offset: " + path, e);
        }
    }

    @Override
    public OutputStream write(String path) throws ConnectorException {
        return write(path, false);
    }

    @Override
    public OutputStream write(String path, boolean append) throws ConnectorException {
        checkInitialized();
        Path resolved = resolvePath(path);

        try {
            Path parent = resolved.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            if (append) {
                return Files.newOutputStream(
                        resolved, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                return Files.newOutputStream(
                        resolved, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            throw new ConnectorException("Failed to write file: " + path, e);
        }
    }

    @Override
    public void delete(String path) throws ConnectorException {
        checkInitialized();
        try {
            Files.deleteIfExists(resolvePath(path));
        } catch (IOException e) {
            throw new ConnectorException("Failed to delete: " + path, e);
        }
    }

    @Override
    public void mkdir(String path) throws ConnectorException {
        checkInitialized();
        try {
            Files.createDirectories(resolvePath(path));
        } catch (IOException e) {
            throw new ConnectorException("Failed to create directory: " + path, e);
        }
    }

    @Override
    public void rename(String sourcePath, String targetPath) throws ConnectorException {
        checkInitialized();
        try {
            Files.move(resolvePath(sourcePath), resolvePath(targetPath));
        } catch (IOException e) {
            throw new ConnectorException("Failed to rename: " + sourcePath, e);
        }
    }

    @Override
    public List<ConfigParameter> getRequiredParameters() {
        return List.of();
    }

    @Override
    public List<ConfigParameter> getOptionalParameters() {
        return List.of(ConfigParameter.optional("basePath", "Base directory", "."));
    }

    @Override
    public boolean supportsResume() {
        return true;
    }

    @Override
    public void close() {
        this.initialized = false;
    }

    private void checkInitialized() throws ConnectorException {
        if (!initialized) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.INVALID_CONFIG, "Connector not initialized");
        }
    }

    private Path resolvePath(String path) throws ConnectorException {
        if (path == null || path.isEmpty() || path.equals(".")) {
            return basePath;
        }

        Path pathObj = Path.of(path);

        // Treat absolute paths as relative to basePath by stripping the root
        // This prevents bypassing basePath containment via absolute paths
        if (pathObj.isAbsolute()) {
            Path root = pathObj.getRoot();
            if (root != null) {
                pathObj = root.relativize(pathObj);
            }
        }

        // Resolve against basePath and normalize to eliminate ".." components
        Path resolved = basePath.resolve(pathObj).normalize();

        // Verify the resolved path stays within basePath (prevents traversal via "..")
        if (!resolved.startsWith(basePath)) {
            throw new ConnectorException(
                    ConnectorException.ErrorCode.INVALID_PATH,
                    "Path traversal not allowed: " + path);
        }

        // If the resolved path exists, resolve symlinks and re-check containment
        if (Files.exists(resolved)) {
            try {
                Path realResolved = resolved.toRealPath();
                Path realBase = basePath.toRealPath();
                if (!realResolved.startsWith(realBase)) {
                    throw new ConnectorException(
                            ConnectorException.ErrorCode.INVALID_PATH,
                            "Path escapes base directory via symlink: " + path);
                }
            } catch (IOException e) {
                throw new ConnectorException(
                        ConnectorException.ErrorCode.INVALID_PATH,
                        "Cannot resolve real path: " + path,
                        e);
            }
        }

        return resolved;
    }

    private static class RandomAccessFileInputStream extends InputStream {
        private final RandomAccessFile raf;

        RandomAccessFileInputStream(RandomAccessFile raf) {
            this.raf = raf;
        }

        @Override
        public int read() throws IOException {
            return raf.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return raf.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            raf.close();
        }
    }
}
