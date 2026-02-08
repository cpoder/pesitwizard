# pesitwizard-connector-api

Service Provider Interface (SPI) for pluggable storage backends. Defines the contract that all storage connectors must implement.

## Key Interfaces and Classes

| Class | Description |
|-------|-------------|
| `StorageConnector` | Core interface: `read`, `write`, `delete`, `exists`, `metadata` operations |
| `ConnectorFactory` | SPI factory interface for creating connector instances |
| `FileMetadata` | File metadata DTO (size, modified date, content type) |
| `ConfigParameter` | Typed configuration parameter definition |
| `ConnectorException` | Base exception for connector operations |

## SPI Discovery

Connectors are discovered via `META-INF/services/com.pesitwizard.connector.ConnectorFactory`. Drop a connector JAR on the classpath and it registers automatically.

## Implementing a Custom Connector

1. Implement `StorageConnector`:

```java
public class MyStorageConnector implements StorageConnector {
    @Override
    public InputStream read(String path, long offset) { ... }

    @Override
    public void write(String path, InputStream data, long size) { ... }

    @Override
    public void delete(String path) { ... }

    @Override
    public boolean exists(String path) { ... }

    @Override
    public FileMetadata metadata(String path) { ... }
}
```

2. Implement `ConnectorFactory`:

```java
public class MyConnectorFactory implements ConnectorFactory {
    @Override
    public String getType() { return "my-storage"; }

    @Override
    public StorageConnector create(Map<String, String> config) { ... }

    @Override
    public List<ConfigParameter> getConfigParameters() { ... }
}
```

3. Register in `META-INF/services/com.pesitwizard.connector.ConnectorFactory`:

```
com.example.MyConnectorFactory
```

## Built-in Implementations

| Module | Type | Description |
|--------|------|-------------|
| `pesitwizard-connector-local` | `local` | Local filesystem |
| `pesitwizard-connector-sftp` | `sftp` | SFTP remote storage |
| `pesitwizard-connector-s3` | `s3` | AWS S3 / MinIO |

## Usage

```xml
<dependency>
    <groupId>com.pesitwizard</groupId>
    <artifactId>pesitwizard-connector-api</artifactId>
    <version>${project.version}</version>
</dependency>
```
