package com.pesitwizard.connector.sftp;

import com.pesitwizard.connector.ConfigParameter;
import com.pesitwizard.connector.ConnectorFactory;
import com.pesitwizard.connector.StorageConnector;
import java.util.List;

public class SftpConnectorFactory implements ConnectorFactory {
    @Override
    public String getType() {
        return "sftp";
    }

    @Override
    public String getName() {
        return "SFTP";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Connect to SFTP servers";
    }

    @Override
    public StorageConnector create() {
        return new SftpConnector();
    }

    @Override
    public List<ConfigParameter> getRequiredParameters() {
        return List.of(
                ConfigParameter.required("host", "SFTP host"),
                ConfigParameter.required("username", "Username"));
    }

    @Override
    public List<ConfigParameter> getOptionalParameters() {
        return List.of(
                ConfigParameter.password("password", "Password"),
                ConfigParameter.integer("port", "Port", 22),
                ConfigParameter.path(
                        "knownHostsFile", "Path to SSH known_hosts file for host key verification"),
                ConfigParameter.path("privateKey", "Path to SSH private key file"),
                ConfigParameter.optional("basePath", "Base directory on the remote server", ""));
    }
}
