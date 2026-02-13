package com.pesitwizard.connector;

import java.util.List;

/**
 * Factory for creating storage connector instances.
 *
 * <p>Implement this interface and register via ServiceLoader to add new connector types.
 *
 * <h2>Registration</h2>
 *
 * <ol>
 *   <li>Implement this interface
 *   <li>Create: META-INF/services/com.pesitwizard.connector.ConnectorFactory
 *   <li>Add your factory class fully qualified name to the file
 * </ol>
 *
 * <h2>Example services file content:</h2>
 *
 * <pre>
 * com.example.MyConnectorFactory
 * </pre>
 */
public interface ConnectorFactory {

    /** Unique type identifier for this connector (e.g., "sftp", "s3", "local") */
    String getType();

    /** Human-readable name */
    String getName();

    /** Connector version */
    String getVersion();

    /** Description of the connector */
    String getDescription();

    /**
     * Create a new connector instance (not yet initialized). Call {@link
     * StorageConnector#initialize(java.util.Map)} after creation.
     */
    StorageConnector create();

    /** Get required configuration parameters */
    List<ConfigParameter> getRequiredParameters();

    /** Get optional configuration parameters */
    default List<ConfigParameter> getOptionalParameters() {
        return List.of();
    }
}
