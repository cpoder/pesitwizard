package com.pesitwizard.connector.s3;

import com.pesitwizard.connector.ConfigParameter;
import com.pesitwizard.connector.ConnectorFactory;
import com.pesitwizard.connector.StorageConnector;
import java.util.List;

public class S3ConnectorFactory implements ConnectorFactory {
    @Override
    public String getType() {
        return "s3";
    }

    @Override
    public String getName() {
        return "AWS S3 / MinIO";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Connect to AWS S3 or MinIO object storage";
    }

    @Override
    public StorageConnector create() {
        return new S3Connector();
    }

    @Override
    public List<ConfigParameter> getRequiredParameters() {
        return List.of(ConfigParameter.required("bucket", "S3 bucket name"));
    }

    @Override
    public List<ConfigParameter> getOptionalParameters() {
        return List.of(
                ConfigParameter.password("accessKey", "AWS Access Key"),
                ConfigParameter.password("secretKey", "AWS Secret Key"));
    }
}
