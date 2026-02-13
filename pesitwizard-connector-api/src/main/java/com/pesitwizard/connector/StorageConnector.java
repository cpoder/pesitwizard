package com.pesitwizard.connector;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Storage Connector Interface - SDK for creating pluggable storage backends.
 *
 * <h2>Overview</h2>
 *
 * Implementations provide access to various storage systems:
 *
 * <ul>
 *   <li>Local filesystem
 *   <li>SFTP/FTP/FTPS servers
 *   <li>AWS S3 / MinIO
 *   <li>Azure Blob Storage
 *   <li>Google Cloud Storage
 *   <li>Custom backends
 * </ul>
 *
 * <h2>Creating a Connector</h2>
 *
 * <ol>
 *   <li>Create a new Maven project with dependency on pesitwizard-connector-api
 *   <li>Implement {@link StorageConnector} and {@link ConnectorFactory}
 *   <li>Create META-INF/services/com.pesitwizard.connector.ConnectorFactory
 *   <li>Add your factory class name to the services file
 *   <li>Package as JAR and drop in pesitwizard-client connectors/ directory
 * </ol>
 *
 * <h2>Hot Reload</h2>
 *
 * Connectors are loaded at startup and can be hot-reloaded by placing new JARs in the connectors/
 * directory. The client periodically scans for new connectors.
 */
public interface StorageConnector extends AutoCloseable {

    /** Unique identifier for this connector type (e.g., "sftp", "s3", "local") */
    String getType();

    /** Human-readable name for this connector */
    String getName();

    /** Connector version */
    String getVersion();

    /**
     * Initialize the connector with configuration parameters. Called once when the connector is
     * loaded.
     *
     * @param config Configuration map (credentials, endpoints, etc.)
     * @throws ConnectorException if initialization fails
     */
    void initialize(Map<String, String> config) throws ConnectorException;

    /**
     * Test connectivity to the storage backend.
     *
     * @return true if connection is successful
     * @throws ConnectorException if connection test fails
     */
    boolean testConnection() throws ConnectorException;

    /**
     * Check if a file exists at the given path.
     *
     * @param path Remote path to check
     * @return true if file exists
     * @throws ConnectorException on error
     */
    boolean exists(String path) throws ConnectorException;

    /**
     * Get file metadata.
     *
     * @param path Remote path
     * @return File metadata
     * @throws ConnectorException if file not found or error
     */
    FileMetadata getMetadata(String path) throws ConnectorException;

    /**
     * List files in a directory.
     *
     * @param path Directory path
     * @return List of file metadata
     * @throws ConnectorException on error
     */
    List<FileMetadata> list(String path) throws ConnectorException;

    /**
     * Open an input stream to read a file. Caller is responsible for closing the stream.
     *
     * @param path Remote path to read
     * @return InputStream for reading file content
     * @throws ConnectorException if file not found or error
     */
    InputStream read(String path) throws ConnectorException;

    /**
     * Read a file starting from a specific position (for resume support).
     *
     * @param path Remote path to read
     * @param offset Byte offset to start reading from
     * @return InputStream positioned at offset
     * @throws ConnectorException on error
     */
    InputStream read(String path, long offset) throws ConnectorException;

    /**
     * Open an output stream to write a file. Caller is responsible for closing the stream.
     *
     * @param path Remote path to write
     * @return OutputStream for writing file content
     * @throws ConnectorException on error
     */
    OutputStream write(String path) throws ConnectorException;

    /**
     * Write a file with append mode (for resume support).
     *
     * @param path Remote path to write
     * @param append If true, append to existing file
     * @return OutputStream for writing
     * @throws ConnectorException on error
     */
    OutputStream write(String path, boolean append) throws ConnectorException;

    /**
     * Delete a file.
     *
     * @param path Remote path to delete
     * @throws ConnectorException on error
     */
    void delete(String path) throws ConnectorException;

    /**
     * Create a directory (and parent directories if needed).
     *
     * @param path Directory path to create
     * @throws ConnectorException on error
     */
    void mkdir(String path) throws ConnectorException;

    /**
     * Rename/move a file.
     *
     * @param sourcePath Source path
     * @param targetPath Target path
     * @throws ConnectorException on error
     */
    void rename(String sourcePath, String targetPath) throws ConnectorException;

    /**
     * Get required configuration parameters for this connector. Used for validation and
     * documentation.
     *
     * @return List of parameter definitions
     */
    List<ConfigParameter> getRequiredParameters();

    /**
     * Get optional configuration parameters for this connector.
     *
     * @return List of optional parameter definitions
     */
    default List<ConfigParameter> getOptionalParameters() {
        return List.of();
    }

    /**
     * Check if this connector supports resume (partial read/write).
     *
     * @return true if resume is supported
     */
    default boolean supportsResume() {
        return false;
    }

    /** Close the connector and release resources. */
    @Override
    void close();
}
