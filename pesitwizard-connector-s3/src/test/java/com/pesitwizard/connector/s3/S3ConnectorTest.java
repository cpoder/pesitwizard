package com.pesitwizard.connector.s3;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pesitwizard.connector.ConfigParameter;
import com.pesitwizard.connector.ConnectorException;
import com.pesitwizard.connector.FileMetadata;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

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
        assertThat(params)
                .extracting(ConfigParameter::getName)
                .containsExactly("accessKey", "secretKey", "region", "endpoint");
    }

    @Test
    void optionalParameters_credentialsAreSensitive() {
        List<ConfigParameter> params = connector.getOptionalParameters();
        ConfigParameter accessKey =
                params.stream()
                        .filter(p -> "accessKey".equals(p.getName()))
                        .findFirst()
                        .orElseThrow();
        ConfigParameter secretKey =
                params.stream()
                        .filter(p -> "secretKey".equals(p.getName()))
                        .findFirst()
                        .orElseThrow();
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
                .satisfies(
                        e ->
                                assertThat(((ConnectorException) e).getErrorCode())
                                        .isEqualTo(ConnectorException.ErrorCode.INVALID_PATH));

        assertThatThrownBy(() -> connector.exists("path\0with\0nulls"))
                .isInstanceOf(ConnectorException.class)
                .satisfies(
                        e ->
                                assertThat(((ConnectorException) e).getErrorCode())
                                        .isEqualTo(ConnectorException.ErrorCode.INVALID_PATH));

        assertThatThrownBy(() -> connector.read("../secret"))
                .isInstanceOf(ConnectorException.class)
                .satisfies(
                        e ->
                                assertThat(((ConnectorException) e).getErrorCode())
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
                .satisfies(
                        e ->
                                assertThat(((ConnectorException) e).getErrorCode())
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

    @Nested
    @ExtendWith(MockitoExtension.class)
    class WithMockS3Client {

        @Mock private S3Client mockS3;

        private S3Connector s3Connector;

        @BeforeEach
        void setUp() throws Exception {
            s3Connector = new S3Connector();
            // Inject mock S3Client and set initialized=true via reflection
            setField(s3Connector, "s3", mockS3);
            setField(s3Connector, "bucket", "test-bucket");
            setField(s3Connector, "prefix", "");
            setField(s3Connector, "initialized", true);
        }

        private void setField(Object target, String fieldName, Object value) throws Exception {
            Field f = S3Connector.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        }

        // --- testConnection ---

        @Test
        void testConnection_success_returnsTrue() throws ConnectorException {
            when(mockS3.headBucket(any(HeadBucketRequest.class)))
                    .thenReturn(HeadBucketResponse.builder().build());

            assertThat(s3Connector.testConnection()).isTrue();
        }

        @Test
        void testConnection_s3Exception_returnsFalse() throws ConnectorException {
            when(mockS3.headBucket(any(HeadBucketRequest.class)))
                    .thenThrow(S3Exception.builder().message("denied").build());

            assertThat(s3Connector.testConnection()).isFalse();
        }

        // --- exists ---

        @Test
        void exists_objectExists_returnsTrue() throws ConnectorException {
            when(mockS3.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(HeadObjectResponse.builder().build());

            assertThat(s3Connector.exists("file.txt")).isTrue();
        }

        @Test
        void exists_noSuchKey_returnsFalse() throws ConnectorException {
            when(mockS3.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("not found").build());

            assertThat(s3Connector.exists("missing.txt")).isFalse();
        }

        // --- getMetadata ---

        @Test
        void getMetadata_found_returnsFileMetadata() throws ConnectorException {
            Instant now = Instant.now();
            when(mockS3.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(
                            HeadObjectResponse.builder()
                                    .contentLength(1024L)
                                    .lastModified(now)
                                    .build());

            FileMetadata meta = s3Connector.getMetadata("doc.pdf");
            assertThat(meta.getName()).isEqualTo("doc.pdf");
            assertThat(meta.getSize()).isEqualTo(1024L);
            assertThat(meta.getLastModified()).isEqualTo(now);
            assertThat(meta.isDirectory()).isFalse();
        }

        @Test
        void getMetadata_notFound_throwsFileNotFound() {
            when(mockS3.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("nope").build());

            assertThatThrownBy(() -> s3Connector.getMetadata("missing.txt"))
                    .isInstanceOf(ConnectorException.class)
                    .satisfies(
                            e ->
                                    assertThat(((ConnectorException) e).getErrorCode())
                                            .isEqualTo(
                                                    ConnectorException.ErrorCode.FILE_NOT_FOUND));
        }

        // --- list ---

        @Test
        void list_returnsFilesAndDirs() throws ConnectorException {
            Instant now = Instant.now();
            ListObjectsV2Response response =
                    ListObjectsV2Response.builder()
                            .contents(
                                    S3Object.builder()
                                            .key("data/file1.txt")
                                            .size(100L)
                                            .lastModified(now)
                                            .build())
                            .commonPrefixes(CommonPrefix.builder().prefix("data/subdir/").build())
                            .isTruncated(false)
                            .build();
            when(mockS3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

            List<FileMetadata> result = s3Connector.list("data");
            assertThat(result).hasSize(2);
            assertThat(result).anyMatch(m -> "file1.txt".equals(m.getName()) && !m.isDirectory());
            assertThat(result).anyMatch(m -> "subdir".equals(m.getName()) && m.isDirectory());
        }

        @Test
        void list_pagination_aggregatesResults() throws ConnectorException {
            ListObjectsV2Response page1 =
                    ListObjectsV2Response.builder()
                            .contents(
                                    S3Object.builder()
                                            .key("data/a.txt")
                                            .size(10L)
                                            .lastModified(Instant.now())
                                            .build())
                            .isTruncated(true)
                            .nextContinuationToken("tok1")
                            .build();
            ListObjectsV2Response page2 =
                    ListObjectsV2Response.builder()
                            .contents(
                                    S3Object.builder()
                                            .key("data/b.txt")
                                            .size(20L)
                                            .lastModified(Instant.now())
                                            .build())
                            .isTruncated(false)
                            .build();
            when(mockS3.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(page1)
                    .thenReturn(page2);

            List<FileMetadata> result = s3Connector.list("data");
            assertThat(result).hasSize(2);
        }

        // --- read ---

        @SuppressWarnings("unchecked")
        @Test
        void read_returnsInputStream() throws ConnectorException {
            ResponseInputStream<GetObjectResponse> mockStream = mock(ResponseInputStream.class);
            when(mockS3.getObject(any(GetObjectRequest.class))).thenReturn(mockStream);

            InputStream stream = s3Connector.read("file.txt");
            assertThat(stream).isNotNull();
        }

        @Test
        void read_withOffset_usesRangeHeader() throws ConnectorException {
            @SuppressWarnings("unchecked")
            ResponseInputStream<GetObjectResponse> mockStream = mock(ResponseInputStream.class);
            when(mockS3.getObject(any(GetObjectRequest.class))).thenReturn(mockStream);

            s3Connector.read("file.txt", 1024);

            verify(mockS3)
                    .getObject(
                            argThat(
                                    (GetObjectRequest req) ->
                                            "bytes=1024-".equals(req.range())
                                                    && "file.txt".equals(req.key())));
        }

        // --- delete ---

        @Test
        void delete_callsDeleteObject() throws ConnectorException {
            s3Connector.delete("old.txt");

            verify(mockS3)
                    .deleteObject(
                            argThat(
                                    (DeleteObjectRequest req) ->
                                            "test-bucket".equals(req.bucket())
                                                    && "old.txt".equals(req.key())));
        }

        // --- rename ---

        @Test
        void rename_copiesThenDeletes() throws ConnectorException {
            when(mockS3.copyObject(any(CopyObjectRequest.class)))
                    .thenReturn(CopyObjectResponse.builder().build());

            s3Connector.rename("src.txt", "dst.txt");

            verify(mockS3)
                    .copyObject(
                            argThat(
                                    (CopyObjectRequest req) ->
                                            "src.txt".equals(req.sourceKey())
                                                    && "dst.txt".equals(req.destinationKey())));
            verify(mockS3)
                    .deleteObject(
                            argThat((DeleteObjectRequest req) -> "src.txt".equals(req.key())));
        }

        // --- prefix resolution ---

        @Test
        void withPrefix_resolvesPathCorrectly() throws Exception {
            setField(s3Connector, "prefix", "myprefix");

            when(mockS3.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(HeadObjectResponse.builder().build());

            s3Connector.exists("file.txt");

            verify(mockS3)
                    .headObject(
                            argThat(
                                    (HeadObjectRequest req) ->
                                            "myprefix/file.txt".equals(req.key())));
        }
    }

    @Nested
    class S3ConnectorFactoryTests {

        private S3ConnectorFactory factory = new S3ConnectorFactory();

        @Test
        void getType_returnsS3() {
            assertThat(factory.getType()).isEqualTo("s3");
        }

        @Test
        void getName_returnsExpected() {
            assertThat(factory.getName()).isEqualTo("AWS S3 / MinIO");
        }

        @Test
        void getVersion_returns100() {
            assertThat(factory.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        void getDescription_notNull() {
            assertThat(factory.getDescription()).isNotNull().isNotEmpty();
        }

        @Test
        void create_returnsS3Connector() {
            assertThat(factory.create()).isInstanceOf(S3Connector.class);
        }

        @Test
        void getRequiredParameters_containsBucket() {
            assertThat(factory.getRequiredParameters())
                    .extracting(ConfigParameter::getName)
                    .contains("bucket");
        }

        @Test
        void getOptionalParameters_containsCredentials() {
            assertThat(factory.getOptionalParameters())
                    .extracting(ConfigParameter::getName)
                    .containsExactly("accessKey", "secretKey");
        }
    }
}
