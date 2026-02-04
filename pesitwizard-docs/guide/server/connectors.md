# Storage Connectors

Connectors allow storing transferred files on different storage backends.

## Available Connectors

| Connector | Module | Description |
|-----------|--------|-------------|
| `local` | `pesitwizard-connector-local` | Local filesystem |
| `sftp` | `pesitwizard-connector-sftp` | Remote SFTP server |
| `s3` | `pesitwizard-connector-s3` | AWS S3 or compatible (MinIO) |

## Local Connector

Storage on the server's local filesystem.

```yaml
pesitwizard:
  connector:
    type: local
    local:
      base-path: /data/pesitwizard/files
      create-directories: true
```

### Directory Structure

```
/data/pesitwizard/files/
├── received/           # Received files
│   └── 2024/01/15/
│       └── file.dat
└── send/               # Files to send
    └── 2024/01/15/
        └── report.csv
```

## SFTP Connector

Storage on a remote SFTP server.

```yaml
pesitwizard:
  connector:
    type: sftp
    sftp:
      host: sftp.example.com
      port: 22
      username: pesitwizard
      # Password authentication
      password: ${SFTP_PASSWORD}
      # OR key authentication
      private-key-file: /app/secrets/id_rsa
      private-key-passphrase: ${KEY_PASSPHRASE}
      # Options
      base-path: /uploads/pesitwizard
      known-hosts-file: /app/config/known_hosts
      strict-host-key-checking: true
```

### SSH Key Generation

```bash
# Generate an ED25519 key pair
ssh-keygen -t ed25519 -f id_rsa -N "" -C "pesitwizard@server"

# Copy the public key to the SFTP server
ssh-copy-id -i id_rsa.pub user@sftp.example.com
```

### Kubernetes Secret for SFTP

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: pesitwizard-sftp
type: Opaque
stringData:
  id_rsa: |
    -----BEGIN OPENSSH PRIVATE KEY-----
    ...
    -----END OPENSSH PRIVATE KEY-----
  known_hosts: |
    sftp.example.com ssh-ed25519 AAAA...
```

## S3 Connector

Storage on AWS S3 or a compatible storage system (MinIO, Ceph, etc.).

```yaml
pesitwizard:
  connector:
    type: s3
    s3:
      endpoint: https://s3.eu-west-1.amazonaws.com
      region: eu-west-1
      bucket: pesitwizard-files
      access-key: ${AWS_ACCESS_KEY_ID}
      secret-key: ${AWS_SECRET_ACCESS_KEY}
      # Options
      prefix: transfers/
      path-style-access: false  # true for MinIO
```

### MinIO Configuration

```yaml
pesitwizard:
  connector:
    type: s3
    s3:
      endpoint: http://minio.minio-system:9000
      region: us-east-1
      bucket: pesitwizard
      access-key: ${MINIO_ACCESS_KEY}
      secret-key: ${MINIO_SECRET_KEY}
      path-style-access: true
```

### AWS IAM Policy

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::pesitwizard-files",
        "arn:aws:s3:::pesitwizard-files/*"
      ]
    }
  ]
}
```

### IRSA Authentication (EKS)

For EKS, use IRSA instead of access keys:

```yaml
# ServiceAccount with IRSA annotation
apiVersion: v1
kind: ServiceAccount
metadata:
  name: pesitwizard
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789:role/pesitwizard-s3
```

```yaml
# Configuration without keys
pesitwizard:
  connector:
    type: s3
    s3:
      region: eu-west-1
      bucket: pesitwizard-files
      # No access-key/secret-key = uses pod credentials
```

## Connector API

The `StorageConnector` interface exposes the following operations:

```java
public interface StorageConnector {
    // Write a file (returns an OutputStream)
    OutputStream write(String path) throws IOException;

    // Write a file with append support
    OutputStream write(String path, boolean append) throws IOException;

    // Read a file
    InputStream read(String path) throws IOException;

    // Read a file with offset support (for resume)
    InputStream read(String path, long offset) throws IOException;

    // Delete a file
    void delete(String path) throws IOException;

    // Check existence
    boolean exists(String path) throws IOException;

    // List files (returns FileMetadata objects)
    List<FileMetadata> list(String path) throws IOException;

    // Create a directory
    void mkdir(String path) throws IOException;

    // Rename a file
    void rename(String from, String to) throws IOException;

    // Get file metadata
    FileMetadata getMetadata(String path) throws IOException;

    // Test the connection to the storage backend
    void testConnection() throws IOException;

    // Initialize the connector with configuration
    void initialize(Map<String, String> config);
}
```

## Custom Connector

You can create your own connector by implementing the interface:

```java
@Component
@ConditionalOnProperty(name = "pesitwizard.connector.type", havingValue = "custom")
public class CustomConnector implements StorageConnector {

    @Override
    public OutputStream write(String path) throws IOException {
        // Your implementation
    }

    @Override
    public OutputStream write(String path, boolean append) throws IOException {
        // Your implementation with append support
    }

    // ... other methods
}
```

## Environment Variables

| Variable | Description |
|----------|-------------|
| `PESITWIZARD_CONNECTOR_TYPE` | Connector type (`local`, `sftp`, `s3`) |
| `SFTP_PASSWORD` | SFTP password |
| `AWS_ACCESS_KEY_ID` | AWS access key |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key |

## Best Practices

::: tip Recommendations
- Use S3 or equivalent for high availability
- Enable server-side encryption (SSE-S3 or SSE-KMS)
- Configure retention/lifecycle policies
- Use IRSA on EKS instead of static keys
:::
