package com.pesitwizard.connector.local;

import com.pesitwizard.connector.ConfigParameter;
import com.pesitwizard.connector.ConnectorFactory;
import com.pesitwizard.connector.StorageConnector;
import java.util.List;

/** Factory for creating local filesystem connectors. */
public class LocalFileConnectorFactory implements ConnectorFactory {

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
    public String getDescription() {
        return "Access files on the local filesystem";
    }

    @Override
    public StorageConnector create() {
        return new LocalFileConnector();
    }

    @Override
    public List<ConfigParameter> getRequiredParameters() {
        return List.of();
    }

    @Override
    public List<ConfigParameter> getOptionalParameters() {
        return List.of(ConfigParameter.optional("basePath", "Base directory", "."));
    }
}
