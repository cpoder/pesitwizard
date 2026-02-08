package com.pesitwizard.connector.s3;

import static org.assertj.core.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.pesitwizard.connector.ConfigParameter;
import com.pesitwizard.connector.ConnectorException;

class S3ConnectorTest {

    private S3Connector connector;

    @BeforeEach
    void setUp() {
        connector = new S3Connector();
    }

    // --- Metadata ---

    @Test
    void getType_returnsS3() {
        assertThat(connector.getType()).isEqualTo("s3");
    }

    @Test
    void getName_returnsExpected() {
        assertThat(connector.getName()).isEqualTo("AWS S3 / MinIO");
    }

    @Test
    void getVersion_returns100() {
        assertThat(connector.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void supportsResume_true() {
        assertThat(connector.supportsResume()).isTrue();
    }

    // --- Parameter definitions ---

    @Test
    void requiredParameters_containsBucket() {
        List<ConfigParameter> params = connector.getRequiredParameters();
        assertThat(params).hasSize(1);
        assertThat(params.get(0).getName()).isEqualTo("bucket");
    }

    @Test
    void optionalParameters_containsExpectedKeys() {
        List<ConfigParameter> params = connector.getOptionalParameters();
        assertThat(params).extracting(ConfigParameter::getName)
                .containsExactly("accessKey", "secretKey", "region", "endpoint");
    }

    @Test
    void optionalParameters_credentialsAreSensitive() {
        List<ConfigParameter> params = connector.getOptionalParameters();
        ConfigParameter accessKey = params.stream().filter(p -> "accessKey".equals(p.getName())).findFirst().orElseThrow();
        ConfigParameter secretKey = params.stream().filter(p -> "secretKey".equals(p.getName())).findFirst().orElseThrow();
        assertThat(accessKey.isSensitive()).isTrue();
        assertThat(secretKey.isSensitive()).isTrue();
    }

    // --- Not-initialized guard ---

    @Test
    void testConnection_beforeInit_throws() {
        assertThatThrownBy(() -> connector.testConnection())
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void exists_beforeInit_throws() {
        assertThatThrownBy(() -> connector.exists("file.txt"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void read_beforeInit_throws() {
        assertThatThrownBy(() -> connector.read("file.txt"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void write_beforeInit_throws() {
        assertThatThrownBy(() -> connector.write("file.txt"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void delete_beforeInit_throws() {
        assertThatThrownBy(() -> connector.delete("file.txt"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void list_beforeInit_throws() {
        assertThatThrownBy(() -> connector.list("/"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void getMetadata_beforeInit_throws() {
        assertThatThrownBy(() -> connector.getMetadata("file.txt"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    @Test
    void rename_beforeInit_throws() {
        assertThatThrownBy(() -> connector.rename("a", "b"))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    // --- Initialization validation ---

    @Test
    void initialize_missingBucket_throws() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("region", "us-east-1");

        assertThatThrownBy(() -> connector.initialize(cfg))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Bucket required");
    }

    // --- Path traversal protection (S3-17) ---

    @Test
    void initialize_thenPathTraversal_throws() throws ConnectorException {
        // Initialize with a fake endpoint that won't actually connect,
        // but we can test path resolution before any S3 call
        // We test via exists() which calls resolve() then checkInit()
        // Since we need initialized=true, we need a successful init.
        // The S3Client.builder().build() may succeed but S3 calls will fail.
        // Instead, test path traversal via the public methods which call resolve().
        // The resolve check happens before the S3 API call.

        // We can test this indirectly: initialize succeeds, then path traversal
        // is rejected before the S3 network call.
        Map<String, String> cfg = new HashMap<>();
        cfg.put("bucket", "test-bucket");
        cfg.put("region", "us-east-1");
        // No endpoint = will try to build real S3 client; may fail in test env
        // Use a fake endpoint to avoid AWS SDK issues
        cfg.put("endpoint", "http://localhost:19999");
        cfg.put("accessKey", "test");
        cfg.put("secretKey", "test");
        connector.initialize(cfg);

        // Path traversal should be rejected before any network call
        assertThatThrownBy(() -> connector.exists("../../etc/passwd"))
                .isInstanceOf(ConnectorException.class)
                .satisfies(e -> assertThat(((ConnectorException) e).getErrorCode())
                        .isEqualTo(ConnectorException.ErrorCode.INVALID_PATH));

        assertThatThrownBy(() -> connector.exists("path\0with\0nulls"))
                .isInstanceOf(ConnectorException.class)
                .satisfies(e -> assertThat(((ConnectorException) e).getErrorCode())
                        .isEqualTo(ConnectorException.ErrorCode.INVALID_PATH));

        assertThatThrownBy(() -> connector.read("../secret"))
                .isInstanceOf(ConnectorException.class)
                .satisfies(e -> assertThat(((ConnectorException) e).getErrorCode())
                        .isEqualTo(ConnectorException.ErrorCode.INVALID_PATH));

        connector.close();
    }

    @Test
    void initialize_nullPath_throws() throws ConnectorException {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("bucket", "test-bucket");
        cfg.put("endpoint", "http://localhost:19999");
        cfg.put("accessKey", "test");
        cfg.put("secretKey", "test");
        connector.initialize(cfg);

        assertThatThrownBy(() -> connector.exists(null))
                .isInstanceOf(ConnectorException.class)
                .satisfies(e -> assertThat(((ConnectorException) e).getErrorCode())
                        .isEqualTo(ConnectorException.ErrorCode.INVALID_PATH));

        connector.close();
    }

    // --- close ---

    @Test
    void close_beforeInit_doesNotThrow() {
        assertThatCode(() -> connector.close()).doesNotThrowAnyException();
    }

    @Test
    void close_afterInit_resetsState() throws ConnectorException {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("bucket", "test-bucket");
        cfg.put("endpoint", "http://localhost:19999");
        cfg.put("accessKey", "test");
        cfg.put("secretKey", "test");
        connector.initialize(cfg);
        connector.close();

        // Should be not-initialized again
        assertThatThrownBy(() -> connector.testConnection())
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("Not initialized");
    }

    // --- mkdir is no-op for S3 ---

    @Test
    void mkdir_doesNotThrow() throws ConnectorException {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("bucket", "test-bucket");
        cfg.put("endpoint", "http://localhost:19999");
        cfg.put("accessKey", "test");
        cfg.put("secretKey", "test");
        connector.initialize(cfg);

        assertThatCode(() -> connector.mkdir("somedir")).doesNotThrowAnyException();

        connector.close();
    }
}
